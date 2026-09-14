package com.yourteam.sahara.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.yourteam.sahara.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerManager(
    private val context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onError: (Int) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer?.setRecognitionListener(this)
        }
    }

    fun startListening(languageCode: String = "en") {
        if (speechRecognizer == null) {
            onError(R.string.voice_not_available)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val localeStr = when (languageCode.lowercase()) {
                "hi" -> "hi-IN"
                "as" -> "as-IN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeStr)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeStr)
        }

        _isListening.value = true
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            onError(R.string.voice_error)
        }
    }

    fun stopListening() {
        _isListening.value = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
    }

    fun destroy() {
        _isListening.value = false
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        // Recognition is still active until onResults/onError, even after audio ends.
    }

    override fun onError(error: Int) {
        if (!_isListening.value) return
        _isListening.value = false
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.no_speech
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.microphone_permission
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_not_available
            else -> R.string.voice_error
        }
        onError(message)
    }

    override fun onResults(results: Bundle?) {
        if (!_isListening.value) return
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val spokenText = matches?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            onSpeechResult(spokenText)
        } else {
            onError(R.string.no_speech)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
