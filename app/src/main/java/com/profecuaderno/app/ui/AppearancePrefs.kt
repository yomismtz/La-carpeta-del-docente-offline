package com.profecuaderno.app.ui

import android.content.Context

data class AppearanceSettings(
    val theme: AgendaThemeStyle = AgendaThemeStyle.MINT_LAVENDER,
    val darkMode: Boolean = false,
    val fontScale: Float = 1f,
    val fontStyle: AppFontStyle = AppFontStyle.SANS
)

object AppearancePrefs {
    private const val PREFS = "appearance_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_DARK = "dark"
    private const val KEY_SCALE = "font_scale"
    private const val KEY_FONT = "font_style"

    fun load(context: Context): AppearanceSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppearanceSettings(
            theme = AgendaThemeStyle.fromKey(prefs.getString(KEY_THEME, null)) ?: AgendaThemeStyle.MINT_LAVENDER,
            darkMode = prefs.getBoolean(KEY_DARK, false),
            fontScale = prefs.getFloat(KEY_SCALE, 1f).coerceIn(.85f, 1.35f),
            fontStyle = AppFontStyle.fromKey(prefs.getString(KEY_FONT, null))
        )
    }

    fun save(context: Context, settings: AppearanceSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, settings.theme.key)
            .putBoolean(KEY_DARK, settings.darkMode)
            .putFloat(KEY_SCALE, settings.fontScale.coerceIn(.85f, 1.35f))
            .putString(KEY_FONT, settings.fontStyle.key)
            .apply()
    }
}
