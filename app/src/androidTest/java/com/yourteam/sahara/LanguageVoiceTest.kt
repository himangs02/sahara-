package com.yourteam.sahara

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.yourteam.sahara.voice.VoiceState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercise the actual device UI, including Activity recreation, without a synthetic Compose clock. */
@RunWith(AndroidJUnit4::class)
class LanguageVoiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SaharaApplication
    private val ui = DeviceUi(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var originalLanguage: AppLanguage

    @Before fun openHome() {
        instrumentation.runOnMainSync {
            originalLanguage = app.languageManager.currentLanguage.value
            app.languageManager.setLanguage(null, AppLanguage.ENGLISH)
        }
        TestSession.signInDemo(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitForText("Welcome to Sahara")
    }

    @After fun restoreLanguage() {
        if (::scenario.isInitialized) scenario.close()
        instrumentation.runOnMainSync {
            app.voiceManager.stopListening()
            if (::originalLanguage.isInitialized) app.languageManager.setLanguage(null, originalLanguage)
        }
    }

    @Test fun languageSwitchUpdatesHomeAndSurvivesActivityRecreation() {
        ui.clickText("हिन्दी")
        ui.waitForText("सहारा में आपका स्वागत है")
        ui.waitForText("मेरी प्रगति")
        ui.clickText("অসমীয়া")
        ui.waitForText("চাহাৰালৈ স্বাগতম")
        scenario.recreate()
        ui.waitForText("চাহাৰালৈ স্বাগতম")
        ui.waitForText("মোৰ অগ্রগতি")
        scenario.onActivity {
            assertEquals(AppLanguage.ASSAMESE, app.languageManager.currentLanguage.value)
            assertEquals("as", app.voiceManager.currentLanguage)
        }
    }

    @Test fun recognizedSpeechNavigatesToAllThreeGames() {
        val phrases = listOf("Start memory game", "Start attention game", "Play sequence game")
        val screenText = listOf("Find the matching pictures.", "Tap the matching symbol below.", "Remember the order of these objects.")
        phrases.zip(screenText).forEach { (phrase, expected) ->
            scenario.onActivity { app.voiceManager.handleSpeechResult(phrase) }
            ui.waitForText(expected)
            instrumentation.waitForIdleSync()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            ui.waitForText("Welcome to Sahara")
        }
    }

    @Test fun progressHomeAndUnknownCommandsBehaveCorrectly() {
        scenario.onActivity { app.voiceManager.handleSpeechResult("Show my progress") }
        ui.waitForText("Activity history")
        scenario.onActivity { app.voiceManager.handleSpeechResult("Go home") }
        ui.waitForText("Welcome to Sahara")
        scenario.onActivity { app.voiceManager.handleSpeechResult("unrecognized gibberish") }
        instrumentation.waitForIdleSync()
        ui.waitForText("Welcome to Sahara")
        scenario.onActivity {
            app.voiceManager.stopListening()
            assertEquals(VoiceState.IDLE, app.voiceManager.voiceState.value)
        }
    }

    @Test fun caregiverScreensFollowSelectedLanguage() {
        ui.clickText("हिन्दी")
        ui.waitForText("सहारा में आपका स्वागत है")
        ui.clickText("देखभालकर्ता")
        ui.waitForText("केयरगिवर डैशबोर्ड")
        ui.waitForText("आपके मरीज़")
        ui.clickText("डैशबोर्ड देखें")
        ui.waitForText("Kamala Devi का डैशबोर्ड")
        ui.waitForText("आज की गतिविधियाँ")
    }

    private fun savedReminder(title: String): Reminder? = runBlocking {
        app.reminderRepository.getRemindersForPatient().first().find { it.title == title }
    }

    @Test fun caregiverReminderAppearsOnHomeAndCanBeMarkedDone() {
        val title = "Evening tea ${SystemClock.uptimeMillis()}"
        ui.clickText("Caregiver")
        ui.clickText("VIEW DASHBOARD")
        ui.waitForText("Activities today")
        ui.scrollUntilVisible("MANAGE DAILY REMINDERS")
        ui.clickText("MANAGE DAILY REMINDERS")
        ui.clickText("Add Reminder")
        ui.typeIntoFirstField(title)
        ui.clickText("Save")
        ui.waitForText(title)

        repeat(3) {
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
        }
        ui.waitForText("Welcome to Sahara")
        ui.scrollUntilVisible(title)
        ui.clickText(title)

        val deadline = SystemClock.uptimeMillis() + 5_000
        while (savedReminder(title)?.lastCompletedEpochDay != LocalClock.now().epochDay) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Reminder was not marked done")
            SystemClock.sleep(100)
        }
        runBlocking { app.reminderRepository.deleteReminder(savedReminder(title)!!) }
    }

    @Test fun localizedPermissionFeedbackAndHindiCommand() {
        ui.clickText("हिन्दी")
        ui.waitForText("सहारा में आपका स्वागत है")
        scenario.onActivity {
            app.voiceManager.permissionDenied()
            assertEquals(VoiceState.ERROR, app.voiceManager.voiceState.value)
            assertEquals(it.getString(R.string.microphone_permission), app.voiceManager.statusMessage.value)
            app.voiceManager.handleSpeechResult("मेरी प्रगति दिखाओ")
        }
        ui.waitForText("गतिविधि का इतिहास")
    }
}
