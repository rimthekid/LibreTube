package com.github.libretube.helpers

import android.content.Context
import android.content.res.Configuration
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.obj.Country
import java.util.Locale

object LocaleHelper {
    fun getAppLocale(): Locale {
        val languageName = PreferenceHelper.getString(PreferenceKeys.LANGUAGE, "sys")
        return when {
            languageName == "sys" -> Locale.getDefault()
            languageName.contains("-") -> {
                val languageParts = languageName.split("-")
                Locale(
                    languageParts[0],
                    languageParts[1].replace("r", "")
                )
            }

            else -> Locale(languageName)
        }
    }

    fun updateLanguage(context: Context) {
        val locale = getAppLocale()
        updateResources(context, locale)
        updateResourcesLegacy(context, locale)
    }

    private fun updateResources(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val configuration: Configuration = context.resources.configuration
        configuration.setLocale(locale)
        context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun updateResourcesLegacy(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.locale = locale
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    private fun getDetectedCountry(context: Context): String {
        return detectSIMCountry(context)
            ?: detectNetworkCountry(context)
            ?: detectLocaleCountry(context)
            ?: "UK"
    }

    private fun detectSIMCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.simCountryIso?.ifEmpty { null }
    }

    private fun detectNetworkCountry(context: Context): String? {
        return context.getSystemService<TelephonyManager>()?.networkCountryIso?.ifEmpty { null }
    }

    private fun detectLocaleCountry(context: Context): String? {
        return ConfigurationCompat.getLocales(context.resources.configuration)[0]!!.country
            .ifEmpty { null }
    }

    fun getAvailableCountries(): List<Country> {
        return Locale.getISOCountries()
            .map { Country(Locale("", it).displayCountry, it) }
            .sortedBy { it.name }
    }

    fun getAvailableLocales(): List<Country> {
        return Locale.getAvailableLocales()
            .distinctBy { it.language }
            .map { Country(it.displayLanguage, it.language) }
            .sortedBy { it.name }
    }

    fun getTrendingRegion(context: Context): String {
        val regionPref = PreferenceHelper.getString(PreferenceKeys.REGION, "sys")

        // get the system default country if auto region selected
        return if (regionPref == "sys") {
            getDetectedCountry(context).uppercase()
        } else {
            regionPref
        }
    }
}
