package com.yourteam.sahara.ai

import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.TodayReminder
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Direction of a metric versus the person's own earlier sessions. For mistakes and time, IMPROVING means fewer / faster. */
enum class TrendDirection { IMPROVING, STABLE, DECLINING, NOT_ENOUGH_DATA }

enum class ChangeStatus { NOT_ENOUGH_HISTORY, NO_NOTABLE_CHANGE, NOTICEABLE_CHANGE }

enum class ActivityStatus { NO_ACTIVITY_YET, ACTIVE_TODAY, ACTIVE_RECENTLY, INACTIVE }

/**
 * The most recent sessions of one activity compared with the sessions just before them, for the
 * same person. There is deliberately no comparison with other people or population norms.
 */
data class PersonalBaseline(
    val recentSessions: Int,
    val baselineSessions: Int,
    val recentAccuracy: Float,
    val baselineAccuracy: Float?,
    /** Standard deviation of accuracy across the baseline sessions. */
    val baselineAccuracySpread: Float?,
    val recentMistakes: Float,
    val baselineMistakes: Float?,
    /** Null when the sessions did not record a completion time. */
    val recentTimeSeconds: Float?,
    val baselineTimeSeconds: Float?,
    /** PerformanceAnalyzer composite scores (0..1). */
    val recentScore: Float,
    val baselineScore: Float?
)

data class ActivitySummary(
    val activityType: CognitiveActivityType,
    val completedSessions: Int,
    val baseline: PersonalBaseline?,
    val accuracyTrend: TrendDirection,
    val mistakesTrend: TrendDirection,
    val timeTrend: TrendDirection,
    /** The level the adaptive engine will use next time. */
    val currentDifficulty: Difficulty,
    val changeStatus: ChangeStatus,
    /** Accuracy of recent completed sessions, oldest first, for the chart. */
    val recentAccuracies: List<Float>,
    val lastSessionAt: Long?
)

data class ReminderSummary(
    val enabled: Int,
    val completed: Int,
    val missed: Int,
    val pending: Int,
    val disabled: Int
)

data class DashboardSummary(
    val status: ActivityStatus,
    val lastActivityAt: Long?,
    val activitiesToday: Int,
    val averageRecentAccuracy: Float?,
    val recommendedActivity: CognitiveActivityType,
    val recommendedDifficulty: Difficulty,
    val activities: List<ActivitySummary>,
    val overallChange: ChangeStatus
) {
    val changedActivities: List<ActivitySummary>
        get() = activities.filter { it.changeStatus == ChangeStatus.NOTICEABLE_CHANGE }
}

/**
 * Turns stored game results into the caregiver dashboard. All rules are simple thresholds so each
 * result can be explained, and the change detector is intentionally conservative: one bad session,
 * a short history, or a recent move to a harder level never produces a "noticeable change".
 */
class CaregiverDashboardAnalyzer(
    private val performanceAnalyzer: PerformanceAnalyzer = PerformanceAnalyzer(),
    private val adaptiveEngine: AdaptiveDifficultyEngine = AdaptiveDifficultyEngine(performanceAnalyzer)
) {

    fun summarize(
        results: List<GameResult>,
        today: LocalClock,
        timeZone: TimeZone = TimeZone.getDefault()
    ): DashboardSummary {
        val completed = results.filter { it.completed }.sortedByDescending { it.timestamp }
        val activities = CognitiveActivityType.entries.map { summarizeActivity(completed, it) }

        val latest = completed.firstOrNull()
        val daysSinceLatest = latest?.let { today.epochDay - LocalClock.at(it.timestamp, timeZone).epochDay }
        val status = when {
            daysSinceLatest == null -> ActivityStatus.NO_ACTIVITY_YET
            daysSinceLatest <= 0 -> ActivityStatus.ACTIVE_TODAY
            daysSinceLatest <= INACTIVE_AFTER_DAYS -> ActivityStatus.ACTIVE_RECENTLY
            else -> ActivityStatus.INACTIVE
        }

        // Same choice as the elderly home screen: the most recently played activity is recommended.
        val recommendedActivity = latest?.let { CognitiveActivityType.fromId(it.gameType) } ?: CognitiveActivityType.MEMORY_MATCH

        return DashboardSummary(
            status = status,
            lastActivityAt = latest?.timestamp,
            activitiesToday = completed.count { LocalClock.at(it.timestamp, timeZone).epochDay == today.epochDay },
            averageRecentAccuracy = completed.take(OVERALL_RECENT_SESSIONS).takeIf { it.isNotEmpty() }
                ?.map { it.accuracy }?.average()?.toFloat(),
            recommendedActivity = recommendedActivity,
            recommendedDifficulty = activities.first { it.activityType == recommendedActivity }.currentDifficulty,
            activities = activities,
            overallChange = when {
                activities.any { it.changeStatus == ChangeStatus.NOTICEABLE_CHANGE } -> ChangeStatus.NOTICEABLE_CHANGE
                activities.any { it.changeStatus == ChangeStatus.NO_NOTABLE_CHANGE } -> ChangeStatus.NO_NOTABLE_CHANGE
                else -> ChangeStatus.NOT_ENOUGH_HISTORY
            }
        )
    }

    fun summarizeActivity(results: List<GameResult>, activityType: CognitiveActivityType): ActivitySummary {
        val sessions = completedSessionsOf(results, activityType)
        val baseline = baseline(sessions)
        return ActivitySummary(
            activityType = activityType,
            completedSessions = sessions.size,
            baseline = baseline,
            accuracyTrend = trend(baseline?.recentAccuracy, baseline?.baselineAccuracy, ACCURACY_TREND_POINTS, higherIsBetter = true, baseline),
            mistakesTrend = trend(baseline?.recentMistakes, baseline?.baselineMistakes, MISTAKES_TREND, higherIsBetter = false, baseline),
            timeTrend = trend(
                baseline?.recentTimeSeconds, baseline?.baselineTimeSeconds,
                max(TIME_TREND_MIN_SECONDS, (baseline?.baselineTimeSeconds ?: 0f) * TIME_TREND_FRACTION),
                higherIsBetter = false, baseline
            ),
            currentDifficulty = adaptiveEngine.analyzeAndRecommend(results, activityType).recommendedDifficulty,
            changeStatus = detectChange(sessions),
            recentAccuracies = sessions.take(CHART_SESSIONS).map { it.accuracy }.reversed(),
            lastSessionAt = sessions.firstOrNull()?.timestamp
        )
    }

    /** [sessions] must be completed sessions of one activity, newest first. */
    fun baseline(sessions: List<GameResult>): PersonalBaseline? {
        if (sessions.isEmpty()) return null
        val recent = sessions.take(RECENT_WINDOW)
        val earlier = sessions.drop(RECENT_WINDOW).take(BASELINE_WINDOW)
        return PersonalBaseline(
            recentSessions = recent.size,
            baselineSessions = earlier.size,
            recentAccuracy = recent.map { it.accuracy }.average().toFloat(),
            baselineAccuracy = earlier.takeIf { it.isNotEmpty() }?.map { it.accuracy }?.average()?.toFloat(),
            baselineAccuracySpread = earlier.takeIf { it.isNotEmpty() }?.let { spread(it.map { r -> r.accuracy }) },
            recentMistakes = recent.map { it.mistakes }.average().toFloat(),
            baselineMistakes = earlier.takeIf { it.isNotEmpty() }?.map { it.mistakes }?.average()?.toFloat(),
            recentTimeSeconds = averageTime(recent),
            baselineTimeSeconds = averageTime(earlier),
            recentScore = performanceAnalyzer.calculateOverallScore(recent),
            baselineScore = earlier.takeIf { it.isNotEmpty() }?.let { performanceAnalyzer.calculateOverallScore(it) }
        )
    }

    /** [sessions] must be completed sessions of one activity, newest first. */
    fun detectChange(sessions: List<GameResult>): ChangeStatus {
        val recent = sessions.take(RECENT_WINDOW)
        val earlier = sessions.drop(RECENT_WINDOW).take(BASELINE_WINDOW)
        if (recent.size < RECENT_WINDOW || earlier.size < MIN_BASELINE_FOR_CHANGE) return ChangeStatus.NOT_ENOUGH_HISTORY

        val baseline = baseline(sessions) ?: return ChangeStatus.NOT_ENOUGH_HISTORY
        val baselineAccuracy = baseline.baselineAccuracy ?: return ChangeStatus.NOT_ENOUGH_HISTORY
        val spread = baseline.baselineAccuracySpread ?: 0f

        // A harder level is expected to lower accuracy; that is not a change in the person.
        val movedToHarderLevel = recent.maxOf { difficultyOrdinal(it) } > earlier.maxOf { difficultyOrdinal(it) }

        val accuracyDrop = baselineAccuracy - baseline.recentAccuracy
        val largeDrop = accuracyDrop >= max(CHANGE_MIN_ACCURACY_DROP, CHANGE_SPREAD_MULTIPLIER * spread)
        // Most recent sessions must each be low, so a single bad session can never trigger it.
        val lowSessions = recent.count { it.accuracy <= baselineAccuracy - max(CHANGE_SESSION_DROP, spread) }
        val scoreDrop = (baseline.baselineScore ?: 0f) - baseline.recentScore

        return if (!movedToHarderLevel && largeDrop && lowSessions >= CHANGE_MIN_LOW_SESSIONS && scoreDrop >= CHANGE_MIN_SCORE_DROP) {
            ChangeStatus.NOTICEABLE_CHANGE
        } else {
            ChangeStatus.NO_NOTABLE_CHANGE
        }
    }

    private fun trend(
        recent: Float?,
        earlier: Float?,
        threshold: Float,
        higherIsBetter: Boolean,
        baseline: PersonalBaseline?
    ): TrendDirection {
        if (recent == null || earlier == null || baseline == null || baseline.baselineSessions < MIN_BASELINE_FOR_TREND) {
            return TrendDirection.NOT_ENOUGH_DATA
        }
        val difference = recent - earlier
        return when {
            abs(difference) < threshold -> TrendDirection.STABLE
            (difference > 0) == higherIsBetter -> TrendDirection.IMPROVING
            else -> TrendDirection.DECLINING
        }
    }

    private fun averageTime(sessions: List<GameResult>): Float? =
        sessions.filter { it.completionTimeSeconds > 0 }.takeIf { it.isNotEmpty() }
            ?.map { it.completionTimeSeconds }?.average()?.toFloat()

    private fun spread(values: List<Float>): Float {
        val mean = values.average()
        return sqrt(values.map { (it - mean) * (it - mean) }.average()).toFloat()
    }

    private fun difficultyOrdinal(result: GameResult) =
        Difficulty.entries.find { it.name == result.difficulty }?.ordinal ?: 0

    companion object {
        const val RECENT_WINDOW = 3
        const val BASELINE_WINDOW = 5
        const val MIN_BASELINE_FOR_TREND = 2
        const val MIN_BASELINE_FOR_CHANGE = 3
        const val ACCURACY_TREND_POINTS = 5f
        const val MISTAKES_TREND = 1f
        const val TIME_TREND_MIN_SECONDS = 5f
        const val TIME_TREND_FRACTION = 0.15f
        const val CHANGE_MIN_ACCURACY_DROP = 15f
        const val CHANGE_SPREAD_MULTIPLIER = 2f
        const val CHANGE_SESSION_DROP = 10f
        const val CHANGE_MIN_LOW_SESSIONS = 2
        const val CHANGE_MIN_SCORE_DROP = 0.1f
        const val OVERALL_RECENT_SESSIONS = 5
        const val INACTIVE_AFTER_DAYS = 3
        const val CHART_SESSIONS = 10

        fun completedSessionsOf(results: List<GameResult>, activityType: CognitiveActivityType): List<GameResult> =
            results.filter {
                it.completed && (it.gameType.equals(activityType.id, ignoreCase = true) ||
                    it.gameType.equals(activityType.displayName, ignoreCase = true))
            }.sortedByDescending { it.timestamp }

        /** Counts only enabled reminders for completed / missed / pending; disabled ones are counted separately. */
        fun summarizeReminders(reminders: List<TodayReminder>): ReminderSummary {
            val enabled = reminders.filter { it.reminder.enabled }
            return ReminderSummary(
                enabled = enabled.size,
                completed = enabled.count { it.status == ReminderStatus.COMPLETED },
                missed = enabled.count { it.status == ReminderStatus.MISSED },
                pending = enabled.count { it.status == ReminderStatus.UPCOMING },
                disabled = reminders.size - enabled.size
            )
        }
    }
}
