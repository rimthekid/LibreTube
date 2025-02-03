package com.github.libretube.helpers

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.text.Spanned
import android.view.Window
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.WindowCompat
import com.github.libretube.R
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.ui.adapters.IconsSheetAdapter
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

object ThemeHelper {

    /**
     * Set the colors of the system bars (status bat and navigation bar)
     */
    fun setSystemBarColors(context: Context, window: Window, @ColorInt bottomNavColor: Int? = null) {
        setStatusBarColor(context, window)
        setNavigationBarColor(context, window, bottomNavColor)
    }

    /**
     * Set the background color of the status bar
     */
    private fun setStatusBarColor(context: Context, window: Window) {
        window.statusBarColor = getThemeColor(context, android.R.attr.colorBackground)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !isDarkMode(context)
    }

    /**
     * Set the background color of the navigation bar
     */
    private fun setNavigationBarColor(
        context: Context,
        window: Window,
        @ColorInt bottomNavColor: Int?
    ) {
        window.navigationBarColor =
            bottomNavColor ?: getThemeColor(context, android.R.attr.colorBackground)
    }

    /**
     * Set the theme, including accent color and night mode
     */
    fun updateTheme(activity: AppCompatActivity) {
        val themeMode = PreferenceHelper.getString(PreferenceKeys.THEME_MODE, "A")

        updateAccentColor(activity)
        applyPureThemeIfEnabled(activity)
        updateThemeMode(themeMode)
    }

    /**
     * Update the accent color of the app and apply dynamic colors if needed
     */
    private fun updateAccentColor(activity: AppCompatActivity) {
        var accentColor = PreferenceHelper.getString(PreferenceKeys.ACCENT_COLOR, "")

        // automatically choose an accent color on the first app startup
        if (accentColor.isEmpty()) {
            accentColor = when (DynamicColors.isDynamicColorAvailable()) {
                true -> "my"
                else -> "blue"
            }
            PreferenceHelper.putString(PreferenceKeys.ACCENT_COLOR, accentColor)
        }

        val theme = when (accentColor) {
            // set the accent color, use the pure black/white theme if enabled
            "my" -> R.style.BaseTheme
            "red" -> R.style.Theme_Red
            "blue" -> R.style.Theme_Blue
            "yellow" -> R.style.Theme_Yellow
            "green" -> R.style.Theme_Green
            "purple" -> R.style.Theme_Purple
            "monochrome" -> R.style.Theme_Monochrome
            "violet" -> R.style.Theme_Violet
            else -> throw IllegalArgumentException()
        }
        activity.setTheme(theme)
        // apply dynamic wallpaper based colors
        if (accentColor == "my") DynamicColors.applyToActivityIfAvailable(activity)
    }

    /**
     * apply the pure black/white theme
     */
    private fun applyPureThemeIfEnabled(activity: Activity) {
        val pureThemeEnabled = PreferenceHelper.getBoolean(
            PreferenceKeys.PURE_THEME,
            false
        )
        if (pureThemeEnabled) activity.theme.applyStyle(R.style.Pure, true)
    }

    fun applyDialogActivityTheme(activity: Activity) {
        activity.theme.applyStyle(R.style.DialogActivity, true)
    }

    /**
     * set the theme mode (light, dark, auto)
     */
    private fun updateThemeMode(themeMode: String) {
        val mode = when (themeMode) {
            "A" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "L" -> AppCompatDelegate.MODE_NIGHT_NO
            "D" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * change the app icon
     */
    fun changeIcon(context: Context, newLogoActivityAlias: String) {
        // Disable Old Icon(s)
        for (appIcon in IconsSheetAdapter.availableIcons) {
            val activityClass = "com.github.libretube." + appIcon.activityAlias

            // remove old icons
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, activityClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // set the class name for the activity alias
        val newLogoActivityClass = "com.github.libretube.$newLogoActivityAlias"
        // Enable New Icon
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, newLogoActivityClass),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Get a color by a color resource attr
     */
    fun getThemeColor(context: Context, colorCode: Int) =
        MaterialColors.getColor(context, colorCode, Color.TRANSPARENT)

    /**
     * Get the styled app name
     */
    fun getStyledAppName(context: Context): Spanned {
        val colorPrimary = getThemeColor(context, androidx.appcompat.R.attr.colorPrimaryDark)
        val hexColor = "#%06X".format(0xFFFFFF and colorPrimary)
        return "Libre<span  style='color:$hexColor';>Tube</span>"
            .parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    fun isDarkMode(context: Context): Boolean {
        val darkModeFlag =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return darkModeFlag == Configuration.UI_MODE_NIGHT_YES
    }
}
