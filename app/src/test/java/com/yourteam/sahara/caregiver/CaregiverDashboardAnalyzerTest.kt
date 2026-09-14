package com.yourteam.sahara.caregiver

import com.yourteam.sahara.ai.ActivityStatus
import com.yourteam.sahara.ai.CaregiverDashboardAnalyzer
import com.yourteam.sahara.ai.ChangeStatus
import com.yourteam.sahara.ai.PerformanceAnalyzer
import com.yourteam.sahara.ai.TrendDirection
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.TodayReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CaregiverDashboardAnalyzerTest {

    private val analyzer = CaregiverDashboardAnalyzer()
    private val memory = CognitiveActivityType.MEMORY_MATCH

    private fun session(
        accuracy: Float,
        timestamp: Long,
        type: CognitiveActivityType = memory,
        mistakes: Int = 1,
        time: Long = 40,
        difficulty: Difficulty = Difficulty.EASY,
        completed: Boolean = true
    ) = GameResult(
        gameType = type.id,
        difficulty = difficulty.name,
        totalPairs = difficulty.pairs,
        matchedPairs = difficulty.pairs,
        mistakes = mistakes,
        completionTimeSeconds = time,
        accuracy = accuracy,
        completed = completed,
        timestamp = timestamp
    )

    /** Builds sessions oldest first from accuracies; later entries get newer timestamps. */
    private fun history(
        accuracies: List<Float>,
        mistakes: List<Int> = accuracies.map { 1 },
        times: List<Long> = accuracies.map { 40L },
        difficulties: List<Difficulty> = accuracies.map { Difficulty.EASY }
    ) = accuracies.indices.map { i ->
        session(accuracies[i], timestamp = 1_000L * (i + 1), mistakes = mistakes[i], time = times[i], difficulty = difficulties[i])
    }

    private fun summarizeMemory(results: List<GameResult>) = analyzer.summarizeActivity(results, memory)

    @Test
    fun `Baseline compares the last three sessions with the five before them`() {
        val results = history(
            accuracies = listOf(10f, 80f, 82f, 84f, 86f, 88f, 70f, 72f, 74f),
            mistakes = listOf(9, 1, 1, 2, 2, 2, 3, 3, 3),
            times = listOf(99, 40, 40, 40, 40, 40, 50, 50, 50)
        ) + listOf(
            session(0f, timestamp = 50_000, completed = false),
            session(20f, timestamp = 60_000, type = CognitiveActivityType.ATTENTION_TAP)
        )

        val sessions = CaregiverDashboardAnalyzer.completedSessionsOf(results, memory)
        val baseline = analyzer.baseline(sessions)!!

        assertEquals(3, baseline.recentSessions)
        assertEquals(5, baseline.baselineSessions)
        assertEquals(72f, baseline.recentAccuracy, 0.01f)
        assertEquals(84f, baseline.baselineAccuracy!!, 0.01f)
        assertEquals(2.828f, baseline.baselineAccuracySpread!!, 0.01f)
        assertEquals(3f, baseline.recentMistakes, 0.01f)
        assertEquals(1.6f, baseline.baselineMistakes!!, 0.01f)
        assertEquals(50f, baseline.recentTimeSeconds!!, 0.01f)
        assertEquals(40f, baseline.baselineTimeSeconds!!, 0.01f)
        assertEquals(PerformanceAnalyzer().calculateOverallScore(sessions.take(3)), baseline.recentScore, 0.0001f)
        assertEquals(9, summarizeMemory(results).completedSessions)
    }

    @Test
    fun `Legacy results stored with the display name still count`() {
        val legacy = session(80f, timestamp = 1).copy(gameType = "Memory Match")
        assertEquals(1, CaregiverDashboardAnalyzer.completedSessionsOf(listOf(legacy), memory).size)
    }

    @Test
    fun `Short history reports not enough data instead of a change`() {
        val four = summarizeMemory(history(listOf(90f, 90f, 40f, 40f)))
        assertEquals(ChangeStatus.NOT_ENOUGH_HISTORY, four.changeStatus)
        assertEquals(TrendDirection.NOT_ENOUGH_DATA, four.accuracyTrend)

        val five = summarizeMemory(history(listOf(90f, 90f, 40f, 40f, 40f)))
        assertEquals(ChangeStatus.NOT_ENOUGH_HISTORY, five.changeStatus)
        assertEquals(TrendDirection.DECLINING, five.accuracyTrend)

        val none = summarizeMemory(emptyList())
        assertNull(none.baseline)
        assertEquals(ChangeStatus.NOT_ENOUGH_HISTORY, none.changeStatus)

        val overall = analyzer.summarize(emptyList(), LocalClock(20_000, 600))
        assertEquals(ChangeStatus.NOT_ENOUGH_HISTORY, overall.overallChange)
        assertEquals(ActivityStatus.NO_ACTIVITY_YET, overall.status)
        assertNull(overall.averageRecentAccuracy)
    }

    @Test
    fun `Stable performance shows steady trends and no change`() {
        val summary = summarizeMemory(history(listOf(80f, 82f, 79f, 81f, 80f, 81f, 79f, 80f)))

        assertEquals(ChangeStatus.NO_NOTABLE_CHANGE, summary.changeStatus)
        assertEquals(TrendDirection.STABLE, summary.accuracyTrend)
        assertEquals(TrendDirection.STABLE, summary.mistakesTrend)
        assertEquals(TrendDirection.STABLE, summary.timeTrend)
    }

    @Test
    fun `Improving performance is shown as a trend but never asks to check in`() {
        val summary = summarizeMemory(
            history(
                accuracies = listOf(60f, 62f, 58f, 61f, 60f, 85f, 88f, 86f),
                mistakes = listOf(5, 5, 6, 5, 5, 1, 1, 2),
                times = listOf(80, 82, 78, 80, 81, 45, 44, 46)
            )
        )

        assertEquals(TrendDirection.IMPROVING, summary.accuracyTrend)
        assertEquals(TrendDirection.IMPROVING, summary.mistakesTrend)
        assertEquals(TrendDirection.IMPROVING, summary.timeTrend)
        assertEquals(ChangeStatus.NO_NOTABLE_CHANGE, summary.changeStatus)
    }

    @Test
    fun `Sustained lower performance across several sessions is a noticeable change`() {
        val results = history(
            accuracies = listOf(88f, 90f, 86f, 89f, 87f, 60f, 55f, 58f),
            mistakes = listOf(1, 1, 1, 1, 1, 5, 6, 5),
            times = listOf(40, 41, 39, 40, 40, 70, 72, 68)
        )
        val summary = summarizeMemory(results)

        assertEquals(ChangeStatus.NOTICEABLE_CHANGE, summary.changeStatus)
        assertEquals(TrendDirection.DECLINING, summary.accuracyTrend)
        assertEquals(TrendDirection.DECLINING, summary.mistakesTrend)
        assertEquals(TrendDirection.DECLINING, summary.timeTrend)

        val overall = analyzer.summarize(results, LocalClock(20_000, 600))
        assertEquals(ChangeStatus.NOTICEABLE_CHANGE, overall.overallChange)
        assertEquals(listOf(memory), overall.changedActivities.map { it.activityType })
    }

    @Test
    fun `A single bad session does not trigger a change`() {
        val summary = summarizeMemory(history(listOf(88f, 90f, 86f, 89f, 87f, 88f, 90f, 40f)))
        assertEquals(ChangeStatus.NO_NOTABLE_CHANGE, summary.changeStatus)
    }

    @Test
    fun `Lower accuracy after moving to a harder level is not flagged`() {
        val easy = List(5) { Difficulty.EASY }
        val medium = List(3) { Difficulty.MEDIUM }
        val summary = summarizeMemory(
            history(
                accuracies = listOf(90f, 92f, 91f, 90f, 93f, 60f, 58f, 62f),
                mistakes = listOf(0, 0, 0, 0, 0, 5, 5, 5),
                difficulties = easy + medium
            )
        )

        assertEquals(TrendDirection.DECLINING, summary.accuracyTrend)
        assertEquals(ChangeStatus.NO_NOTABLE_CHANGE, summary.changeStatus)
    }

    @Test
    fun `A naturally variable baseline needs a larger drop`() {
        val summary = summarizeMemory(
            history(
                accuracies = listOf(50f, 95f, 60f, 100f, 55f, 52f, 50f, 55f),
                mistakes = listOf(4, 0, 3, 0, 4, 5, 5, 5)
            )
        )
        assertEquals(ChangeStatus.NO_NOTABLE_CHANGE, summary.changeStatus)
    }

    @Test
    fun `Reminder summary counts only enabled reminders by status`() {
        fun item(id: String, status: ReminderStatus, enabled: Boolean = true) =
            TodayReminder(Reminder(id = id, title = id, minuteOfDay = 60, enabled = enabled), status)

        val summary = CaregiverDashboardAnalyzer.summarizeReminders(
            listOf(
                item("done", ReminderStatus.COMPLETED),
                item("missed", ReminderStatus.MISSED),
                item("later1", ReminderStatus.UPCOMING),
                item("later2", ReminderStatus.UPCOMING),
                item("off", ReminderStatus.MISSED, enabled = false)
            )
        )

        assertEquals(4, summary.enabled)
        assertEquals(1, summary.completed)
        assertEquals(1, summary.missed)
        assertEquals(2, summary.pending)
        assertEquals(1, summary.disabled)
    }

    @Test
    fun `Activity aggregation counts completed sessions on the local day`() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val base = 1_789_243_200_000L // 2026-09-12T20:00Z = 01:30 on 13 September in India
        val hour = 3_600_000L
        val results = listOf(
            session(80f, timestamp = base - 3 * hour),                                            // 22:30 on the 12th
            session(70f, timestamp = base + 1 * hour),                                            // 02:30 on the 13th
            session(90f, timestamp = base + 2 * hour, type = CognitiveActivityType.ATTENTION_TAP), // 03:30 on the 13th
            session(10f, timestamp = base + 3 * hour, type = CognitiveActivityType.SEQUENCE_RECALL, completed = false)
        )
        val now = base + 10 * hour

        val summary = analyzer.summarize(results, LocalClock.at(now, india), india)
        assertEquals(2, summary.activitiesToday)
        assertEquals(ActivityStatus.ACTIVE_TODAY, summary.status)
        assertEquals(base + 2 * hour, summary.lastActivityAt)
        assertEquals(80f, summary.averageRecentAccuracy!!, 0.01f)
        assertEquals(CognitiveActivityType.ATTENTION_TAP, summary.recommendedActivity)
        assertEquals(Difficulty.EASY, summary.recommendedDifficulty)
        assertEquals(3, summary.activities.size)

        // In UTC the same sessions fall on the 12th, which is "yesterday" at 06:00Z on the 13th.
        val utc = TimeZone.getTimeZone("UTC")
        val utcSummary = analyzer.summarize(results, LocalClock.at(now, utc), utc)
        assertEquals(0, utcSummary.activitiesToday)
        assertEquals(ActivityStatus.ACTIVE_RECENTLY, utcSummary.status)

        val day = 24 * hour
        assertEquals(ActivityStatus.ACTIVE_RECENTLY, analyzer.summarize(results, LocalClock.at(now + 2 * day, india), india).status)
        assertEquals(ActivityStatus.INACTIVE, analyzer.summarize(results, LocalClock.at(now + 5 * day, india), india).status)
    }

    @Test
    fun `Recent accuracies for the chart are oldest first`() {
        val summary = summarizeMemory(history(listOf(50f, 60f, 70f)))
        assertEquals(listOf(50f, 60f, 70f), summary.recentAccuracies)
        assertTrue(summary.lastSessionAt == 3_000L)
    }
}
