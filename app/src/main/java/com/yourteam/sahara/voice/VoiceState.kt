package com.yourteam.sahara.voice

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

fun VoiceState.toDisplayText(
    listeningText: String = "I'm listening...",
    processingText: String = "Understanding...",
    speakingText: String = "Sahara is speaking...",
    errorText: String = "Sorry, I didn't understand.",
    idleText: String = "Tap to Speak"
): String {
    return when (this) {
        VoiceState.IDLE -> idleText
        VoiceState.LISTENING -> listeningText
        VoiceState.PROCESSING -> processingText
        VoiceState.SPEAKING -> speakingText
        VoiceState.ERROR -> errorText
    }
}
