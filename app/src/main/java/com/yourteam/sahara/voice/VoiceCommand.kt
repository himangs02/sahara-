package com.yourteam.sahara.voice

import java.util.Locale

enum class VoiceCommand {
    START_MEMORY_GAME, START_ATTENTION_GAME, START_SEQUENCE_GAME,
    SHOW_PROGRESS, GO_HOME, HELP, UNKNOWN;

    companion object {
        private fun normalize(text: String) = text.trim().lowercase(Locale.ROOT)
            .replace('’', '\'').replace(Regex("[.!?।]+$"), "")
            .replace(Regex("\\s+"), " ").removePrefix("please ").removeSuffix(" please")

        private val phrases = mapOf(
            START_MEMORY_GAME to listOf("memory", "memory game", "start memory game", "play memory game",
                "start memory training", "मेमोरी गेम शुरू करो", "मेमोरी गेम शुरू करें", "मेमोरी",
                "মেম’ৰী গেম আৰম্ভ কৰক", "মেমৰী গেম আৰম্ভ কৰক", "স্মৃতি গেম আৰম্ভ কৰক", "স্মৃতি"),
            START_ATTENTION_GAME to listOf("attention", "attention game", "start attention game",
                "start attention training", "play attention game", "ध्यान वाला गेम शुरू करो",
                "ध्यान गेम शुरू करें", "ध्यान", "মনোযোগ গেম আৰম্ভ কৰক", "মনোযোগ"),
            START_SEQUENCE_GAME to listOf("sequence", "sequence game", "start sequence game",
                "play sequence game", "start sequence recall", "क्रम वाला गेम शुरू करो",
                "क्रम याद करने वाला गेम शुरू करो", "क्रम", "ক্ৰম গেম আৰম্ভ কৰক", "ক্ৰম"),
            SHOW_PROGRESS to listOf("progress", "show my progress", "show progress", "show my history",
                "मेरी प्रगति दिखाओ", "मेरी प्रगति दिखाएँ", "মোৰ অগ্রগতি দেখুৱাওক", "মোৰ অগ্ৰগতি দেখুৱাওক"),
            GO_HOME to listOf("home", "go home", "go back home", "घर जाओ", "मुख्य पृष्ठ पर जाओ",
                "ঘৰলৈ যাওক", "মূল পৃষ্ঠালৈ যাওক"),
            HELP to listOf("help", "help me", "मदद", "मदद करो", "সহায়", "সহায় কৰক")
        ).flatMap { (command, aliases) -> aliases.map { normalize(it) to command } }.toMap()

        fun parse(text: String): VoiceCommand = phrases[normalize(text)] ?: UNKNOWN
    }
}
