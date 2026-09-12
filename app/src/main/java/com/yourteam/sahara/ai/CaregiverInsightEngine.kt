package com.yourteam.sahara.ai

import com.yourteam.sahara.model.AlertSeverity
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CaregiverInsight
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.InsightType

data class ActivityChangeBreakdown(
    val activityType: CognitiveActivityType,
    val previousAccuracy: Float,
    val recentAccuracy: Float,
    val previousTimeSeconds: Long,
    val recentTimeSeconds: Long,
    val previousMistakes: Float,
    val recentMistakes: Float,
    val interpretation: String
)

class CaregiverInsightEngine {

    fun generateInsights(allResults: List<GameResult>): List<CaregiverInsight> {
        val completed = allResults.filter { it.completed }.sortedByDescending { it.timestamp }
        if (completed.isEmpty()) {
            return listOf(
                CaregiverInsight(
                    activityType = null,
                    title = "Baseline Activity Started",
                    summary = "The patient has started using Sahara. Activity trends will appear as games are completed.",
                    observations = listOf("No completed sessions recorded yet."),
                    type = InsightType.STABLE
                )
            )
        }

        val insights = mutableListOf<CaregiverInsight>()

        CognitiveActivityType.entries.forEach { activityType ->
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
                        val obs = mutableListOf<String>()
                        if (accDrop >= 10f) obs.add("Recent accuracy decreased by ${accDrop.toInt()}% compared to baseline.")
                        if (timeIncrease > 5f) obs.add("Recent response times are slower by ${timeIncrease.toInt()} seconds.")
                        if (mistakeIncrease > 0.5f) obs.add("More mistakes were recorded in recent sessions.")

                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = "${activityType.displayName} Performance Changed",
                                summary = "${activityType.displayName} performance has decreased compared with recent sessions.",
                                observations = obs,
                                type = InsightType.DECLINE
                            )
                        )
                    } else if (recentAcc - prevAcc >= 10f) {
                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = "${activityType.displayName} Performance Improving",
                                summary = "${activityType.displayName} performance has shown consistent improvement.",
                                observations = listOf("Accuracy increased by ${(recentAcc - prevAcc).toInt()}%.", "Mistakes decreased in recent sessions."),
                                type = InsightType.IMPROVING
                            )
                        )
                    } else {
                        insights.add(
                            CaregiverInsight(
                                activityType = activityType,
                                title = "${activityType.displayName} Performance Stable",
                                summary = "${activityType.displayName} performance is currently stable.",
                                observations = listOf("Consistent accuracy around ${recentAcc.toInt()}%.", "Stable reaction and completion pace."),
                                type = InsightType.STABLE
                            )
                        )
                    }
                } else {
                    insights.add(
                        CaregiverInsight(
                            activityType = activityType,
                            title = "${activityType.displayName} Initial Pace",
                            summary = "Initial sessions recorded for ${activityType.displayName}.",
                            observations = listOf("Average accuracy: ${recentAcc.toInt()}%.", "Average completion time: ${recentTimeAvg.toInt()}s."),
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
                    title = "Overall Activity Stable",
                    summary = "Cognitive activity engagement and performance remain consistent.",
                    observations = listOf("Activity participation is ongoing.", "No negative performance variance detected."),
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
                            title = "Attention Needed: ${activityType.displayName}",
                            message = "${activityType.displayName} performance has been below the patient's recent baseline for 3 consecutive sessions.",
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
                        title = "Inactivity Reminder",
                        message = "No cognitive activity sessions have been recorded in the past 3 days.",
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

        val interpretation = if (recentAcc < prevAcc) {
            "Recent ${activityType.displayName} sessions show lower accuracy and slower responses compared to previous baseline."
        } else if (recentAcc > prevAcc) {
            "Recent ${activityType.displayName} sessions show improved accuracy and faster response times."
        } else {
            "Recent ${activityType.displayName} performance is consistent with the patient's baseline performance."
        }

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
