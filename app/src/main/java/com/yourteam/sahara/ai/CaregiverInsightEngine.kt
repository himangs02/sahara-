package com.yourteam.sahara.ai

import com.yourteam.sahara.R
import com.yourteam.sahara.model.AlertKind
import com.yourteam.sahara.model.AlertSeverity
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CaregiverInsight
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.InsightType
import com.yourteam.sahara.model.UiText

data class ActivityChangeBreakdown(
    val activityType: CognitiveActivityType,
    val previousAccuracy: Float,
    val recentAccuracy: Float,
    val previousTimeSeconds: Long,
    val recentTimeSeconds: Long,
    val previousMistakes: Float,
    val recentMistakes: Float,
    val interpretation: UiText
)

class CaregiverInsightEngine {

    fun generateInsights(allResults: List<GameResult>): List<CaregiverInsight> {
        val completed = allResults.filter { it.completed }.sortedByDescending { it.timestamp }
        if (completed.isEmpty()) {
            return listOf(
                CaregiverInsight(
                    activityType = null,
                    title = UiText(R.string.insight_baseline_title),
                    summary = UiText(R.string.insight_baseline_summary),
                    observations = listOf(UiText(R.string.insight_no_sessions)),
                    type = InsightType.STABLE
                )
            )
        }

        val insights = mutableListOf<CaregiverInsight>()

        CognitiveActivityType.entries.forEach { activityType ->
            val name = UiText(activityType.titleRes)
            val activityResults = completed.filter {
                it.gameType.equals(activityType.id, ignoreCase = true) ||
                it.gameType.equals(activityType.displayName, ignoreCase = true)
            }

            if (activityResults.size >= 2) {
                val recent = activityResults.take(3)
                val previous = activityResults.drop(3).take(3)

                val recentAcc = recent.map { it.accuracy }.average().toFloat()
                val recentMistakesAvg = recent.map { it.mistakes }.average().toFloat()
                val recentTimeAvg = recent.map { it.completionTimeSeconds }.average().toFloat()

                if (previous.isNotEmpty()) {
                    val prevAcc = previous.map { it.accuracy }.average().toFloat()
                    val prevMistakesAvg = previous.map { it.mistakes }.average().toFloat()
                    val prevTimeAvg = previous.map { it.completionTimeSeconds }.average().toFloat()

                    val accDrop = prevAcc - recentAcc
                    val timeIncrease = recentTimeAvg - prevTimeAvg
                    val mistakeIncrease = recentMistakesAvg - prevMistakesAvg

                    if (accDrop >= 10f || mistakeIncrease >= 1.5f) {
                        val obs = mutableListOf<UiText>()
                        if (accDrop >= 10f) obs.add(UiText(R.string.obs_accuracy_dropped, accDrop.toInt()))
                        if (timeIncrease > 5f) obs.add(UiText(R.string.obs_slower, timeIncrease.toInt()))
                        if (mistakeIncrease > 0.5f) obs.add(UiText(R.string.obs_more_mistakes))

                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = UiText(R.string.insight_decline_title, name),
                                summary = UiText(R.string.insight_decline_summary, name),
                                observations = obs,
                                type = InsightType.DECLINE
                            )
                        )
                    } else if (recentAcc - prevAcc >= 10f) {
                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = UiText(R.string.insight_improving_title, name),
                                summary = UiText(R.string.insight_improving_summary, name),
                                observations = listOf(
                                    UiText(R.string.obs_accuracy_increased, (recentAcc - prevAcc).toInt()),
                                    UiText(R.string.obs_fewer_mistakes)
                                ),
                                type = InsightType.IMPROVING
                            )
                        )
                    } else {
                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = UiText(R.string.insight_stable_title, name),
                                summary = UiText(R.string.insight_stable_summary, name),
                                observations = listOf(
                                    UiText(R.string.obs_consistent_accuracy, recentAcc.toInt()),
                                    UiText(R.string.obs_stable_pace)
                                ),
                                type = InsightType.STABLE
                            )
                        )
                    }
                } else {
                    insights.add(
                        CaregiverInsight(
                            activityType = activityType,
                            title = UiText(R.string.insight_initial_title, name),
                            summary = UiText(R.string.insight_initial_summary, name),
                            observations = listOf(
                                UiText(R.string.obs_average_accuracy, recentAcc.toInt()),
                                UiText(R.string.obs_average_time, recentTimeAvg.toInt())
                            ),
                            type = InsightType.STABLE
                        )
                    )
                }
            }
        }

        if (insights.isEmpty()) {
            insights.add(
                CaregiverInsight(
                    activityType = null,
                    title = UiText(R.string.insight_overall_title),
                    summary = UiText(R.string.insight_overall_summary),
                    observations = listOf(UiText(R.string.obs_participation), UiText(R.string.obs_no_negative)),
                    type = InsightType.STABLE
                )
            )
        }

        return insights
    }

    fun generateAlerts(allResults: List<GameResult>): List<CaregiverAlert> {
        val alerts = mutableListOf<CaregiverAlert>()
        val completed = allResults.filter { it.completed }.sortedByDescending { it.timestamp }

        if (completed.isEmpty()) {
            return emptyList()
        }

        // Check for 3 consecutive low performance sessions (< 60% accuracy) per activity
        CognitiveActivityType.entries.forEach { activityType ->
            val activityResults = completed.filter {
                it.gameType.equals(activityType.id, ignoreCase = true) ||
                it.gameType.equals(activityType.displayName, ignoreCase = true)
            }

            if (activityResults.size >= 3) {
                val recent3 = activityResults.take(3)
                if (recent3.all { it.accuracy < 60f }) {
                    alerts.add(
                        CaregiverAlert(
                            id = "alert_low_${activityType.id}",
                            kind = AlertKind.LOW_PERFORMANCE,
                            activityType = activityType,
                            severity = AlertSeverity.WARNING,
                            timestamp = recent3.first().timestamp
                        )
                    )
                }
            }
        }

        // Check for long period without activity (> 3 days)
        val latestSession = completed.firstOrNull()
        if (latestSession != null) {
            val threeDaysMillis = 3 * 24 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - latestSession.timestamp > threeDaysMillis) {
                alerts.add(
                    CaregiverAlert(
                        id = "alert_inactivity",
                        kind = AlertKind.INACTIVITY,
                        activityType = null,
                        severity = AlertSeverity.INFO,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        return alerts
    }

    fun getWhyChangeBreakdown(
        allResults: List<GameResult>,
        activityType: CognitiveActivityType
    ): ActivityChangeBreakdown {
        val completed = allResults
            .filter {
                it.completed && (
                    it.gameType.equals(activityType.id, ignoreCase = true) ||
                    it.gameType.equals(activityType.displayName, ignoreCase = true)
                )
            }
            .sortedByDescending { it.timestamp }

        val recent = completed.take(3)
        val previous = completed.drop(3).take(3)

        val recentAcc = if (recent.isNotEmpty()) recent.map { it.accuracy }.average().toFloat() else 0f
        val prevAcc = if (previous.isNotEmpty()) previous.map { it.accuracy }.average().toFloat() else recentAcc

        val recentTime = if (recent.isNotEmpty()) recent.map { it.completionTimeSeconds }.average().toLong() else 0L
        val prevTime = if (previous.isNotEmpty()) previous.map { it.completionTimeSeconds }.average().toLong() else recentTime

        val recentMistakes = if (recent.isNotEmpty()) recent.map { it.mistakes }.average().toFloat() else 0f
        val prevMistakes = if (previous.isNotEmpty()) previous.map { it.mistakes }.average().toFloat() else recentMistakes

        val interpretation = UiText(
            when {
                recentAcc < prevAcc -> R.string.interpretation_lower
                recentAcc > prevAcc -> R.string.interpretation_higher
                else -> R.string.interpretation_same
            },
            UiText(activityType.titleRes)
        )

        return ActivityChangeBreakdown(
            activityType = activityType,
            previousAccuracy = prevAcc,
            recentAccuracy = recentAcc,
            previousTimeSeconds = prevTime,
            recentTimeSeconds = recentTime,
            previousMistakes = prevMistakes,
            recentMistakes = recentMistakes,
            interpretation = interpretation
        )
    }
}
