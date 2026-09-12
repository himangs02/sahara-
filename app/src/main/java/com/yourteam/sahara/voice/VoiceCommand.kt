package com.yourteam.sahara.voice

enum class VoiceCommand {
    START_MEMORY_GAME,
    START_ATTENTION_GAME,
    START_SEQUENCE_GAME,
    SHOW_PROGRESS,
    GO_HOME,
    HELP,
    UNKNOWN;

    companion object {
        fun parse(text: String): VoiceCommand {
            val input = text.trim().lowercase()

            return when {
                // Memory Game
                input.contains("memory") || input.contains("मेमोरी") || input.contains("স্মৃতি") -> START_MEMORY_GAME
                
                // Attention Game
                input.contains("attention") || input.contains("ध्यान") || input.contains("মনোযোগ") -> START_ATTENTION_GAME
                
                // Sequence Game
                input.contains("sequence") || input.contains("order") || input.contains("क्रम") || input.contains("ক্ৰম") -> START_SEQUENCE_GAME
                
                // Show Progress
                input.contains("progress") || input.contains("activity") || input.contains("history") || 
                input.contains("प्रगति") || input.contains("অগ্রগতি") -> SHOW_PROGRESS
                
                // Go Home
                input.contains("home") || input.contains("main") || input.contains("घर") || input.contains("ঘৰ") -> GO_HOME
                
                // Help
                input.contains("help") || input.contains("मदद") || input.contains("সহায়") -> HELP

                else -> UNKNOWN
            }
        }
    }
}
