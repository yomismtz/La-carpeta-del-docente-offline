package com.profecuaderno.app.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SPANISH
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SPANISH }

fun AppLanguage.text(es: String, en: String): String = if (this == AppLanguage.ENGLISH) en else es

object AppLanguagePrefs {
    private const val PREFS = "agenda_preferences"
    private const val KEY = "app_language"

    fun load(context: Context): AppLanguage =
        AppLanguage.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "es"))

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
    }
}
