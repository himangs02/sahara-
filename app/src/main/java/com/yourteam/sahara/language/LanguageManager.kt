package com.yourteam.sahara.language

import android.app.Activity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
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

        /**
         * Matches a stored preference that may be an ISO code ("as"), an enum name ("ASSAMESE"),
         * or the English display name ("Assamese") -- Patient.language and
         * AuthRepository.SUPPORTED_LANGUAGES both use the display-name form, while AppLanguage's
         * own persistence uses the code, so this is the single place that reconciles them
         * (Stage 3D, Part 2: one source of truth from patient preference to UI/voice language).
         * Returns null (never a silent English default) when nothing matches, so a caller can
         * decide whether "unrecognized" should change anything at all.
         */
        fun fromStoredPreference(stored: String?): AppLanguage? {
            if (stored.isNullOrBlank()) return null
            return entries.find {
                it.code.equals(stored, ignoreCase = true) || it.name.equals(stored, ignoreCase = true) || it.displayName == stored
            }
        }
    }
}

class LanguageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("sahara_language_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(resolveEffectiveLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    /**
     * Resolves which language the app is *actually* presenting, in priority order:
     *   1. the locale AppCompat has already applied to this process
     *   2. an explicit choice the user saved previously
     *   3. the device's own locale
     *
     * Note the deliberate absence of a hardcoded English default: on a Hindi
     * handset with no saved choice, Android already resolves resources to
     * values-hi, so reporting ENGLISH here would desync the selector from what
     * is on screen.
     */
    private fun resolveEffectiveLanguage(): AppLanguage {
        val applied = appliedLocales()
        if (!applied.isEmpty) {
            return AppLanguage.fromCode(applied[0]?.language ?: "en")
        }
        if (prefs.contains(KEY_SELECTED_LANGUAGE)) {
            return getSavedLanguage()
        }
        val systemLanguage = context.resources.configuration.locales[0]?.language ?: "en"
        return AppLanguage.fromCode(systemLanguage)
    }

    /**
     * Applies the persisted language to the process. Call once from
     * SaharaApplication.onCreate() -- NOT from a Composable. Applying a locale
     * recreates the Activity, and doing that as a side effect of composition
     * caused the NavHost to reset mid-composition.
     */
    fun applySavedLanguageIfNeeded() {
        // Android 13 owns persisted locales. Read it directly: AppCompat may not
        // have an Activity delegate yet during Application.onCreate.
        if (Build.VERSION.SDK_INT >= 33) {
            val platform = context.getSystemService(LocaleManager::class.java)
            if (!prefs.getBoolean("platform_locale_migrated", false)) {
                if (platform.applicationLocales.isEmpty && prefs.contains(KEY_SELECTED_LANGUAGE)) {
                    platform.applicationLocales = LocaleList.forLanguageTags(getSavedLanguage().code)
                }
                prefs.edit().putBoolean("platform_locale_migrated", true).apply()
            }
            refreshFromConfiguration()
            return
        }
        if (!prefs.contains(KEY_SELECTED_LANGUAGE)) {
            // The user has never chosen a language. Leave the system locale
            // alone rather than forcing English on them.
            _currentLanguage.value = resolveEffectiveLanguage()
            return
        }

        val saved = getSavedLanguage()
        val applied = AppCompatDelegate.getApplicationLocales()
        if (applied.isEmpty || applied[0]?.language != saved.code) {
            applyLocale(saved.code)
        }
        _currentLanguage.value = saved
    }

    @Suppress("UNUSED_PARAMETER")
    fun setLanguage(activity: Activity?, language: AppLanguage) {
        // Deliberately no `if (_currentLanguage.value == language) return` guard.
        // The StateFlow and the applied locale can legitimately disagree -- for
        // example when SharedPreferences was written by an earlier build that
        // never applied the locale. With the guard in place, tapping the language
        // that prefs already claimed was selected did nothing, and the mismatch
        // was unrecoverable without clearing app data.
        saveLanguage(language)
        _currentLanguage.value = language
        applyLocale(language.code)
        // AppCompatDelegate.setApplicationLocales handles Activity recreation itself.
    }

    fun getLocale(): Locale {
        return when (_currentLanguage.value.code.lowercase()) {
            "hi" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            "as" -> Locale.Builder().setLanguage("as").setRegion("IN").build()
            else -> Locale.ENGLISH
        }
    }

    private fun applyLocale(languageCode: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageCode)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
        }
    }

    private fun appliedLocales(): LocaleListCompat = if (Build.VERSION.SDK_INT >= 33) {
        LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).applicationLocales)
    } else AppCompatDelegate.getApplicationLocales()

    fun refreshFromConfiguration() {
        val applied = appliedLocales()
        val code = if (!applied.isEmpty) applied[0]?.language else if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales[0]?.language
        } else context.resources.configuration.locales[0]?.language
        _currentLanguage.value = AppLanguage.fromCode(code ?: "en")
    }

    private fun saveLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, language.code).apply()
    }

    fun getSavedLanguage(): AppLanguage {
        val code = prefs.getString(KEY_SELECTED_LANGUAGE, "en") ?: "en"
        return AppLanguage.fromCode(code)
    }

    private companion object {
        const val KEY_SELECTED_LANGUAGE = "key_selected_language"
    }
}
