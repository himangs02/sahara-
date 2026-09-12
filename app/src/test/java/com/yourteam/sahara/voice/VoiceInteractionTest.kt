package com.yourteam.sahara.voice

import com.yourteam.sahara.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceInteractionTest {

    @Test
    fun `English voice command recognition - Memory Game`() {
        val command = VoiceCommand.parse("Start memory game")
        assertEquals(VoiceCommand.START_MEMORY_GAME, command)
    }

    @Test
    fun `English voice command recognition - Attention Game`() {
        val command = VoiceCommand.parse("Start attention training")
        assertEquals(VoiceCommand.START_ATTENTION_GAME, command)
    }

    @Test
    fun `English voice command recognition - Sequence Game`() {
        val command = VoiceCommand.parse("Play sequence game")
        assertEquals(VoiceCommand.START_SEQUENCE_GAME, command)
    }

    @Test
    fun `English voice command recognition - Progress`() {
        val command = VoiceCommand.parse("Show my progress")
        assertEquals(VoiceCommand.SHOW_PROGRESS, command)
    }

    @Test
    fun `English voice command recognition - Home`() {
        val command = VoiceCommand.parse("Go home")
        assertEquals(VoiceCommand.GO_HOME, command)
    }

    @Test
    fun `Hindi voice command mapping - Memory Game`() {
        val command = VoiceCommand.parse("मेमोरी गेम शुरू करो")
        assertEquals(VoiceCommand.START_MEMORY_GAME, command)
    }

    @Test
    fun `Hindi voice command mapping - Progress`() {
        val command = VoiceCommand.parse("मेरी प्रगति दिखाओ")
        assertEquals(VoiceCommand.SHOW_PROGRESS, command)
    }

    @Test
    fun `Assamese voice command mapping - Attention`() {
        val command = VoiceCommand.parse("মনোযোগ গেম আৰম্ভ কৰক")
        assertEquals(VoiceCommand.START_ATTENTION_GAME, command)
    }

    @Test
    fun `Unknown command returns UNKNOWN without crashing`() {
        val command = VoiceCommand.parse("Unrecognized random gibberish phrase")
        assertEquals(VoiceCommand.UNKNOWN, command)
    }

    @Test
    fun `Empty speech returns UNKNOWN`() {
        val command = VoiceCommand.parse("")
        assertEquals(VoiceCommand.UNKNOWN, command)
    }

    @Test
    fun `Voice state text formatting`() {
        assertEquals("Tap to Speak", VoiceState.IDLE.toDisplayText())
        assertEquals("I'm listening...", VoiceState.LISTENING.toDisplayText())
        assertEquals("Understanding...", VoiceState.PROCESSING.toDisplayText())
        assertEquals("Sahara is speaking...", VoiceState.SPEAKING.toDisplayText())
        assertEquals("Sorry, I didn't understand.", VoiceState.ERROR.toDisplayText())
    }

    @Test
    fun `Language enum codes and fallbacks`() {
        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("hi", AppLanguage.HINDI.code)
        assertEquals("as", AppLanguage.ASSAMESE.code)

        val lang = AppLanguage.fromCode("as")
        assertEquals(AppLanguage.ASSAMESE, lang)

        val fallback = AppLanguage.fromCode("unknown_code")
        assertEquals(AppLanguage.ENGLISH, fallback)
    }
}
