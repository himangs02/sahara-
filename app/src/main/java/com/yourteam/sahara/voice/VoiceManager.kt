package com.yourteam.sahara.voice

import android.content.Context
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yourteam.sahara.R
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceManager(
    private val context: Context,
    private val onCommandRecognized: (VoiceCommand) -> Unit = {}
) {
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap to Speak")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _commands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    var currentLanguage: String = "en"
        set(value) {
            if (field != value) {
                stopListening()
                field = value
            }
            ttsManager.setLanguage(value)
            _statusMessage.value = message(R.string.tap_to_speak)
        }

    private fun message(id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(currentLanguage))
        return context.createConfigurationContext(config).getString(id)
    }

    fun permissionDenied() {
        _voiceState.value = VoiceState.ERROR
        _statusMessage.value = message(R.string.microphone_permission)
    }

    val ttsManager: TextToSpeechManager = TextToSpeechManager(context)

    private val recognizerManager: SpeechRecognizerManager = SpeechRecognizerManager(
        context = context,
        onSpeechResult = { text ->
            handleSpeechResult(text)
        },
        onError = { error ->
            _voiceState.value = VoiceState.ERROR
            _statusMessage.value = message(error)
        }
    )

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied()
            return
        }
        if (_voiceState.value == VoiceState.LISTENING) return
        ttsManager.stop()
        _voiceState.value = VoiceState.LISTENING
        _statusMessage.value = message(R.string.listening)
        recognizerManager.startListening(currentLanguage)
    }

    fun stopListening() {
        recognizerManager.stopListening()
        ttsManager.stop()
        _voiceState.value = VoiceState.IDLE
        _statusMessage.value = message(R.string.tap_to_speak)
    }

    fun handleSpeechResult(text: String) {
        _lastRecognizedText.value = text
        _voiceState.value = VoiceState.PROCESSING
        _statusMessage.value = message(R.string.processing)

        val command = VoiceCommand.parse(text)
        val responseSpeech = getReassuringResponse(command)
        // Navigation must remain available even without a TTS voice installed.
        _commands.tryEmit(command)
        onCommandRecognized(command)

        _voiceState.value = VoiceState.SPEAKING
        _statusMessage.value = responseSpeech

        ttsManager.speak(responseSpeech) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = message(R.string.tap_to_speak)
        }
    }

    fun speakPrompt(text: String) {
        _voiceState.value = VoiceState.SPEAKING
        _statusMessage.value = text
        ttsManager.speak(text) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = message(R.string.tap_to_speak)
        }
    }

    private fun getReassuringResponse(command: VoiceCommand): String {
        return when (currentLanguage.lowercase()) {
            "hi" -> when (command) {
                VoiceCommand.START_MEMORY_GAME -> "चलिए आपकी मेमोरी गतिविधि शुरू करते हैं।"
                VoiceCommand.START_ATTENTION_GAME -> "आइए ध्यान वाला गेम शुरू करते हैं।"
                VoiceCommand.START_SEQUENCE_GAME -> "क्रम याद करने वाला गेम शुरू कर रहे हैं।"
                VoiceCommand.SHOW_PROGRESS -> "यह रही आपकी हाल की गतिविधि।"
                VoiceCommand.GO_HOME -> "मुख्य पृष्ठ पर वापस जा रहे हैं।"
                VoiceCommand.HELP -> "आप कह सकते हैं: मेमोरी गेम शुरू करो, या मेरी प्रगति दिखाओ।"
                VoiceCommand.UNKNOWN -> "क्षमा करें, मैं समझ नहीं पाया।"
            }
            "as" -> when (command) {
                VoiceCommand.START_MEMORY_GAME -> "আহক আপোনাৰ মেম’ৰী কাৰ্যসূচী আৰম্ভ কৰোঁ।"
                VoiceCommand.START_ATTENTION_GAME -> "আহক মনোযোগ কাৰ্যসূচী আৰম্ভ কৰোঁ।"
                VoiceCommand.START_SEQUENCE_GAME -> "ক্ৰম মনত ৰখা কাৰ্যসূচী আৰম্ভ কৰিছো।"
                VoiceCommand.SHOW_PROGRESS -> "এইয়া আপোনাৰ শেহতীয়া অগ্রগতি।"
                VoiceCommand.GO_HOME -> "মূল পৃষ্ঠালৈ ঘূৰি গৈছো।"
                VoiceCommand.HELP -> "আপুনি ক’ব পাৰে: মেম’ৰী গেম আৰম্ভ কৰক, বা মোৰ অগ্রগতি দেখুৱাওক।"
                VoiceCommand.UNKNOWN -> "ক্ষমা কৰিব, মই বুজি নাপালোঁ।"
            }
            else -> when (command) {
                VoiceCommand.START_MEMORY_GAME -> "Let's start your memory training activity."
                VoiceCommand.START_ATTENTION_GAME -> "Opening Attention Tap activity for you."
                VoiceCommand.START_SEQUENCE_GAME -> "Starting Sequence Recall activity."
                VoiceCommand.SHOW_PROGRESS -> "Here is your activity progress."
                VoiceCommand.GO_HOME -> "Returning to Home."
                VoiceCommand.HELP -> "You can say: Start memory game, Show my progress, or Go home."
                VoiceCommand.UNKNOWN -> "Sorry, I didn't understand. Tap to try again."
            }
        }
    }

    fun destroy() {
        recognizerManager.destroy()
        ttsManager.shutdown()
    }
}
