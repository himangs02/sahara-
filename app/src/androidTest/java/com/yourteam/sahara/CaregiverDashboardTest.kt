package com.yourteam.sahara

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CaregiverDashboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SaharaApplication
    private val ui = DeviceUi(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var originalLanguage: AppLanguage

    private fun seedSession(index: Int, type: CognitiveActivityType, accuracy: Float, mistakes: Int, minutesAgo: Int) {
        val result = GameResult(
            syncId = "$TEST_PREFIX$index",
            gameType = type.id,
            difficulty = Difficulty.EASY.name,
            totalPairs = Difficulty.EASY.pairs,
            matchedPairs = Difficulty.EASY.pairs,
            mistakes = mistakes,
            completionTimeSeconds = 40L + mistakes * 5,
            accuracy = accuracy,
            completed = true,
            timestamp = System.currentTimeMillis() - minutesAgo * 60_000L
        )
        // Straight to the DAO so the test does not add items to the sync queue.
        app.database.gameResultDao().insertGameResult(result.toEntity())
    }

    @Before fun seed() {
        instrumentation.runOnMainSync { originalLanguage = app.languageManager.currentLanguage.value }
        // Memory Match: five steady sessions, then three clearly lower ones -> a noticeable change.
        listOf(88f, 90f, 86f, 89f, 87f, 60f, 55f, 58f).forEachIndexed { i, accuracy ->
            seedSession(i, CognitiveActivityType.MEMORY_MATCH, accuracy, mistakes = if (accuracy > 80) 1 else 5, minutesAgo = 80 - i * 10)
        }
        // Attention Tap: a short history. Sequence Recall has none.
        seedSession(20, CognitiveActivityType.ATTENTION_TAP, 75f, mistakes = 2, minutesAgo = 5)
        seedSession(21, CognitiveActivityType.ATTENTION_TAP, 80f, mistakes = 1, minutesAgo = 4)
    }

    @After fun cleanUp() {
        if (::scenario.isInitialized) scenario.close()
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM game_results WHERE syncId LIKE '$TEST_PREFIX%'")
        instrumentation.runOnMainSync {
            if (::originalLanguage.isInitialized) app.languageManager.setLanguage(null, originalLanguage)
        }
    }

    private fun localized(language: AppLanguage): Context {
        val config = Configuration(instrumentation.targetContext.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.code))
        return instrumentation.targetContext.createConfigurationContext(config)
    }

    /** Opens the patient dashboard in [language] and returns resources in that language. */
    private fun openDashboard(language: AppLanguage): Context {
        instrumentation.runOnMainSync { app.languageManager.setLanguage(null, language) }
        val strings = localized(language)
        TestSession.signInDemo(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Signed in already: the Caregiver tab opens the caregiver dashboard directly.
        ui.clickText(strings.getString(R.string.nav_caregiver))
        ui.clickText(strings.getString(R.string.view_dashboard))
        ui.waitForText(strings.getString(R.string.patient_dashboard_title, "Kamala Devi"))
        return strings
    }

    private fun Context.scrollTo(id: Int, vararg args: Any): String {
        val text = getString(id, *args)
        ui.scrollUntilVisible(text)
        ui.waitForText(text)
        return text
    }

    @Test fun dashboardShowsOverviewChangePerformanceRoutineAndHistory() {
        val s = openDashboard(AppLanguage.ENGLISH)

        // Overview
        ui.waitForText(s.getString(R.string.stat_activities_today))
        ui.waitForText(s.getString(R.string.status_active_today))
        ui.waitForText(s.getString(R.string.stat_recent_accuracy))
        ui.waitForText(s.getString(R.string.stat_recommended_level))
        ui.waitForText(s.getString(R.string.stat_reminders_today))

        // Conservative change notice for the seeded Memory Match drop
        s.scrollTo(R.string.change_noticeable)
        s.scrollTo(R.string.change_check_in)

        // Cognitive summaries for all three activities
        s.scrollTo(R.string.memory_match)
        ui.waitForText(s.getString(R.string.noticeable_change_chip))
        s.scrollTo(R.string.attention_tap)
        s.scrollTo(R.string.sequence_recall)
        s.scrollTo(R.string.no_sessions_yet)

        // Today's routine from the reminder system
        s.scrollTo(R.string.todays_routine)
        s.scrollTo(R.string.morning_medicine)
        s.scrollTo(R.string.manage_reminders)

        // Activity history opens
        s.scrollTo(R.string.full_history)
        ui.clickText(s.getString(R.string.full_history))
        ui.waitForText(s.getString(R.string.recent_activity_history))
        ui.waitForText(s.getString(R.string.memory_match))
        ui.waitForText(s.getString(R.string.reminder_completed))
    }

    @Test fun dashboardAccessibilityLabelsExist() {
        val s = openDashboard(AppLanguage.ENGLISH)
        ui.waitForText(s.getString(R.string.back))
        val minTouchPx = (47.5f * s.resources.displayMetrics.density).toInt()

        assertTrue(ui.inaccessibleClickables(minTouchPx).joinToString("\n"), ui.inaccessibleClickables(minTouchPx).isEmpty())

        s.scrollTo(R.string.memory_match)
        // The accuracy chart is described in words, not only drawn.
        ui.waitForLabelStartingWith(s.getString(R.string.accuracy_chart_description, 8, "").substringBefore(":"))

        s.scrollTo(R.string.todays_routine)
        ui.waitForText(s.getString(R.string.type_medicine))
        s.scrollTo(R.string.manage_reminders)
        val problems = ui.inaccessibleClickables(minTouchPx)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun assertDashboardIn(language: AppLanguage) {
        val s = openDashboard(language)
        val english = localized(AppLanguage.ENGLISH)
        listOf(R.string.stat_activities_today, R.string.change_card_title, R.string.todays_routine).forEach {
            assertNotEquals(english.getString(it), s.getString(it))
        }

        ui.waitForText(s.getString(R.string.stat_activities_today))
        s.scrollTo(R.string.change_noticeable)
        s.scrollTo(R.string.memory_match)
        s.scrollTo(R.string.todays_routine)
        s.scrollTo(R.string.recent_sessions)
    }

    @Test fun dashboardStringsAreTranslated() {
        val ids = listOf(
            R.string.dashboard_loading, R.string.patient_age_region, R.string.preferred_language,
            R.string.status_active_today, R.string.status_active_recently, R.string.status_inactive,
            R.string.status_no_activity, R.string.last_activity, R.string.stat_activities_today,
            R.string.stat_recent_accuracy, R.string.stat_recommended_level, R.string.stat_reminders_today,
            R.string.reminders_done_count, R.string.change_card_title, R.string.change_not_enough,
            R.string.change_not_enough_detail, R.string.change_none, R.string.change_noticeable,
            R.string.change_check_in, R.string.change_activity_detail, R.string.baseline_disclaimer,
            R.string.trend_higher, R.string.trend_lower, R.string.trend_fewer, R.string.trend_more,
            R.string.trend_faster, R.string.trend_slower, R.string.trend_steady, R.string.trend_not_enough,
            R.string.no_sessions_yet, R.string.noticeable_change_chip, R.string.accuracy_chart_description,
            R.string.todays_routine, R.string.routine_summary, R.string.reminder_pending,
            R.string.recent_sessions, R.string.session_not_finished, R.string.last_played
        )
        val english = localized(AppLanguage.ENGLISH)
        mapOf(AppLanguage.HINDI to Regex("[\\u0900-\\u097F]"), AppLanguage.ASSAMESE to Regex("[\\u0980-\\u09FF]")).forEach { (language, script) ->
            val strings = localized(language)
            ids.forEach { id ->
                val name = english.resources.getResourceEntryName(id)
                val text = strings.getString(id)
                assertNotEquals("${language.code}/$name is untranslated", english.getString(id), text)
                assertTrue("${language.code}/$name is not in the expected script: $text", script.containsMatchIn(text))
            }
            val sessions = strings.resources.getQuantityString(R.plurals.sessions_count, 2, 2)
            assertTrue("${language.code}/sessions_count: $sessions", script.containsMatchIn(sessions))
        }
    }

    @Test fun dashboardWorksInHindi() = assertDashboardIn(AppLanguage.HINDI)

    @Test fun dashboardWorksInAssamese() = assertDashboardIn(AppLanguage.ASSAMESE)

    private companion object {
        const val TEST_PREFIX = "dashboard-test-"
    }
}
