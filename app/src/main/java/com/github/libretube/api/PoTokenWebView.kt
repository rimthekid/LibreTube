package com.github.libretube.api

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import com.github.libretube.BuildConfig
import kotlinx.coroutines.*
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PoTokenWebView private constructor(
    context: Context,
    private val generatorContinuation: Continuation<PoTokenWebView>
) {
    private val webView = WebView(context)
    private val poTokenContinuations = mutableMapOf<String, Continuation<String>>()
    private lateinit var expirationInstant: Instant

    //region Initialization
    init {
        val webViewSettings = webView.settings
         //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webViewSettings.safeBrowsingEnabled = false
        }
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
       webView.addJavascriptInterface(this, JS_INTERFACE)
     }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                withContext(Dispatchers.Main) {
                    webView.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html.replace(
                            "</script>",
                            // calls downloadAndRunBotguard() when the page has finished loading
                            "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                        ),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            } catch (e: Exception) {
                onInitializationError(e)
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "downloadAndRunBotguard() called")
        }

        CoroutineScope(Dispatchers.IO).launch {
            val responseBody = makeBotguardServiceRequest(
                "https://www.youtube.com/api/jnn/v1/Create",
                listOf(REQUEST_KEY)
            )
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    """(async function() {
                    try {
                        data = JSON.parse(String.raw`$responseBody`)
                        result = await runBotGuard(data)
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error.toString())
                    }
                })();""",
                ) {}
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Initialization error from JavaScript: $error")
        }
        onInitializationError(PoTokenException(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = makeBotguardServiceRequest(
                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                listOf(REQUEST_KEY, botguardResponse)
            )
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    """(async function() {
                        try {
                            this.integrityToken = JSON.parse(String.raw`$response`)
                            $JS_INTERFACE.onInitializationFinished(integrityToken[1])
                        } catch (error) {
                            $JS_INTERFACE.onJsInitializationError(error.toString())
                        }
                    })();""", null
                )
            }
        }
    }

    /**
     * Called during initialization by the JavaScript snippet from [onRunBotguardResult] when the
     * `integrityToken` has been received by JavaScript.
     *
     * @param expirationTimeInSeconds in how many seconds the integrity token expires, can be found
     * in `integrityToken[1]`
     */
    @JavascriptInterface
    fun onInitializationFinished(expirationTimeInSeconds: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onInitializationFinished() called, expiration=${expirationTimeInSeconds}s")
        }
        // leave 10 minutes of margin just to be sure
        expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)
        generatorContinuation.resume(this)
    }
    //endregion

    //region Obtaining poTokens
    suspend fun generatePoToken(identifier: String): String {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "generatePoToken() called with identifier $identifier")
        }
        return suspendCancellableCoroutine { continuation ->
            poTokenContinuations[identifier] = continuation

            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(
                    """(async function() {
                        identifier = String.raw`$identifier`
                        try {
                            poToken = await obtainPoToken(webPoSignalOutput, integrityToken, identifier)
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poToken)
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error.toString())
                        }
                    })();""", null
                )
            }
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        }
        poTokenContinuations.remove(identifier)?.resumeWithException(PoTokenException(error))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poToken: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        }
        poTokenContinuations.remove(identifier)?.resume(poToken)
    }

    fun isExpired(): Boolean {
        return Instant.now().isAfter(expirationInstant)
    }
    //endregion

    //region Utils
    /**
     * Makes a POST request to [url] with the given [data] by setting the correct headers.
     * This is supposed to be used only during initialization. Returns the  response body
     * as a String if the response is successful.
     */
    private suspend fun makeBotguardServiceRequest(url: String, data: List<String>): String = withContext(Dispatchers.IO) {
        val response = RetrofitInstance.externalApi.botguardRequest(url, data)
        response.toString()
    }

    /**
     * Handles any error happening during initialization, releasing resources and sending the error
     * to [generatorContinuation].
     */
    private fun onInitializationError(error: Throwable) {
        CoroutineScope(Dispatchers.Main).launch {
            close()
            generatorContinuation.resumeWithException(error)
        }
    }

    /**
     * Releases all [webView] resources.
     */
    @MainThread
    fun close() {
        webView.clearHistory()
        // clears RAM cache and disk cache (globally for all WebViews)
        webView.clearCache(true)

        // ensures that the WebView isn't doing anything when destroying it
        webView.loadUrl("about:blank")

        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }
    //endregion

    companion object {
        private val TAG = PoTokenWebView::class.simpleName
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val JS_INTERFACE = "PoTokenWebView"

        suspend fun newPoTokenGenerator(context: Context): PoTokenWebView {
            return suspendCancellableCoroutine { continuation ->
                Handler(Looper.getMainLooper()).post {
                    val poTokenWebView = PoTokenWebView(context, continuation)
                    poTokenWebView.loadHtmlAndObtainBotguard(context)
                }
            }
        }
    }
}


class PoTokenException(message: String) : Exception(message)