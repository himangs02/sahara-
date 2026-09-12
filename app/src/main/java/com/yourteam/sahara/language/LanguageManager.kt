package com.yourteam.sahara.language

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    ASSAMESE("as", "অসমীয়া");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

class LanguageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("sahara_language_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(getSavedLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    init {
        // Apply saved language on startup if it differs from current application locales
        val savedLang = getSavedLanguage()
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty || currentLocales[0]?.language != savedLang.code) {
            applyLocale(savedLang.code)
        }
    }

    fun setLanguage(activity: Activity?, language: AppLanguage) {
        if (_currentLanguage.value == language) return
        _currentLanguage.value = language
        saveLanguage(language)
        applyLocale(language.code)
        // Note: AppCompatDelegate.setApplicationLocales handles Activity recreation automatically
    }

    fun getLocale(): Locale {
        return when (getSavedLanguage().code.lowercase()) {
            "hi" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            "as" -> Locale.Builder().setLanguage("as").setRegion("IN").build()
            else -> Locale.ENGLISH
        }
    }

    private fun applyLocale(languageCode: String) {
        val appLocales = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    private fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString("key_selected_language", language.code).apply()
    }

    fun getSavedLanguage(): AppLanguage {
        val code = prefs.getString("key_selected_language", "en") ?: "en"
        return AppLanguage.fromCode(code)
    }
}
