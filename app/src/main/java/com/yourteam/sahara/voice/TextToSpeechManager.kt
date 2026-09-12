package com.yourteam.sahara.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                language = Locale.ENGLISH
                setSpeechRate(0.85f) // Clear, slow, reassuring for elderly
                setPitch(1.0f)
            }
            isInitialized = true
            onInitComplete?.invoke(true)
        } else {
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    fun setLanguage(languageCode: String): Boolean {
        if (!isInitialized || tts == null) return false
        val locale = when (languageCode.lowercase()) {
            "hi" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            "as" -> Locale.Builder().setLanguage("as").setRegion("IN").build()
            else -> Locale.ENGLISH
        }
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(
        text: String,
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SaharaTTS_${System.currentTimeMillis()}")
        onDone?.invoke()
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}
