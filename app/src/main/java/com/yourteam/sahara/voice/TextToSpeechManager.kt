package com.yourteam.sahara.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.Locale

class TextToSpeechManager(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeId: String? = null
    private var completion: (() -> Unit)? = null
    private var languageAvailable = false

    private fun finish(id: String?) {
        mainHandler.post {
            if (id != null && activeId == id) {
                val callback = completion
                activeId = null
                completion = null
                callback?.invoke()
            }
        }
    }

    // TextToSpeech initialises asynchronously. setLanguage() is routinely called
    // before onInit() fires, so remember the request and apply it on init instead
    // of dropping it and locking the engine to English.
    private var pendingLanguageCode: String = "en"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                languageAvailable = setLanguage(localeFor(pendingLanguageCode)) >= 0
                setSpeechRate(0.85f) // Clear, slow, reassuring for elderly
                setPitch(1.0f)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = finish(utteranceId)
                    @Deprecated("Platform callback")
                    override fun onError(utteranceId: String?) = finish(utteranceId)
                })
            }
            isInitialized = true
            onInitComplete?.invoke(true)
        } else {
            isInitialized = false
            onInitComplete?.invoke(false)
        }
    }

    private fun localeFor(languageCode: String): Locale = when (languageCode.lowercase()) {
        "hi" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
        "as" -> Locale.Builder().setLanguage("as").setRegion("IN").build()
        else -> Locale.ENGLISH
    }

    fun setLanguage(languageCode: String): Boolean {
        // Record the request first, so a call made before onInit() is not lost.
        pendingLanguageCode = languageCode
        if (!isInitialized || tts == null) return false
        val result = tts?.setLanguage(localeFor(languageCode))
        languageAvailable = result != null && result >= 0
        return languageAvailable
    }

    fun speak(
        text: String,
        onDone: (() -> Unit)? = null
    ) {
        stop()
        if (!isInitialized || tts == null || !languageAvailable) {
            onDone?.invoke()
            return
        }

        val id = UUID.randomUUID().toString()
        activeId = id
        completion = onDone
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finish(id)
    }

    fun stop() {
        activeId = null
        completion = null
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
