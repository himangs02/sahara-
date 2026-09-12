package com.yourteam.sahara.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceManager(
    context: Context,
    private val onCommandRecognized: (VoiceCommand) -> Unit = {}
) {
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap to Speak")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    var currentLanguage: String = "en"

    val ttsManager: TextToSpeechManager = TextToSpeechManager(context)

    private val recognizerManager: SpeechRecognizerManager = SpeechRecognizerManager(
        context = context,
        onSpeechResult = { text ->
            handleSpeechResult(text)
        },
        onError = { error ->
            _voiceState.value = VoiceState.ERROR
            _statusMessage.value = error
            ttsManager.speak(error)
        }
    )

    fun startListening() {
        if (_voiceState.value == VoiceState.SPEAKING) {
            ttsManager.stop()
        }
        _voiceState.value = VoiceState.LISTENING
        _statusMessage.value = "I'm listening..."
        recognizerManager.startListening(currentLanguage)
    }

    fun stopListening() {
        recognizerManager.stopListening()
        _voiceState.value = VoiceState.IDLE
        _statusMessage.value = "Tap to Speak"
    }

    fun handleSpeechResult(text: String) {
        _lastRecognizedText.value = text
        _voiceState.value = VoiceState.PROCESSING
        _statusMessage.value = "Understanding..."

        val command = VoiceCommand.parse(text)
        val responseSpeech = getReassuringResponse(command)

        _voiceState.value = VoiceState.SPEAKING
        _statusMessage.value = responseSpeech

        ttsManager.speak(responseSpeech) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to Speak"
            onCommandRecognized(command)
        }
    }

    fun speakPrompt(text: String) {
        _voiceState.value = VoiceState.SPEAKING
        _statusMessage.value = text
        ttsManager.speak(text) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to Speak"
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
