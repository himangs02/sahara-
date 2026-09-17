package com.yourteam.sahara.ai

import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult

class ActivityRecommendationEngine {

    private val activities = listOf(
        "Memory Match",
        "Attention Tap",
        "Sequence Recall"
    )

    fun recommend(
        results: List<GameResult>
    ): ActivityRecommendation {

        val completed = results
            .filter { it.completed }
            .sortedByDescending { it.timestamp }

        if (completed.size < 3) {
            return ActivityRecommendation(
                gameType = "Memory Match",
                difficulty = Difficulty.EASY,
                reason = "Sahara is learning your activity pattern.",
                trend = ActivityTrend.INSUFFICIENT_DATA,
                confidence = 0.3f
            )
        }

        val recentActivities = completed
            .take(3)
            .map { it.gameType }

        val recommendedGame = activities
            .minByOrNull { activity ->
                recentActivities.count { it == activity }
            }
            ?: "Memory Match"

        val activityResults = completed
            .filter { it.gameType == recommendedGame }

        if (activityResults.size < 3) {
            return ActivityRecommendation(
                gameType = recommendedGame,
                difficulty = Difficulty.EASY,
                reason = "This activity has had less recent practice.",
                trend = ActivityTrend.INSUFFICIENT_DATA,
                confidence = 0.4f
            )
        }

        val recent = activityResults.take(3)

        val recentAccuracy = recent
            .map { it.accuracy }
            .average()
            .toFloat()

        if (activityResults.size < 6) {
            return ActivityRecommendation(
                gameType = recommendedGame,
                difficulty = Difficulty.EASY,
                reason = "Sahara is learning your activity pattern.",
                trend = ActivityTrend.INSUFFICIENT_DATA,
                confidence = 0.5f
            )
        }

        val previous = activityResults
            .drop(3)
            .take(3)

        val previousAccuracy = previous
            .map { it.accuracy }
            .average()
            .toFloat()

        val accuracyChange = recentAccuracy - previousAccuracy

        val trend = when {
            accuracyChange >= 0.10f ->
                ActivityTrend.IMPROVING

            accuracyChange <= -0.15f ->
                ActivityTrend.NEEDS_ATTENTION

            else ->
                ActivityTrend.STABLE
        }

        val difficulty = when {
            trend == ActivityTrend.IMPROVING &&
                    recentAccuracy >= 0.80f ->
                Difficulty.MEDIUM

            trend == ActivityTrend.NEEDS_ATTENTION ->
                Difficulty.EASY

            else ->
                Difficulty.EASY
        }

        val reason = when (trend) {
            ActivityTrend.IMPROVING ->
                "Your recent performance is improving."

            ActivityTrend.NEEDS_ATTENTION ->
                "Let's keep this activity comfortable and steady."

            ActivityTrend.STABLE ->
                "Your recent performance has been steady."

            ActivityTrend.INSUFFICIENT_DATA ->
                "Sahara is learning your activity pattern."
        }

        val confidence = when {
            activityResults.size >= 10 -> 1.0f
            activityResults.size >= 6 -> 0.7f
            activityResults.size >= 3 -> 0.5f
            else -> 0.3f
        }

        return ActivityRecommendation(
            gameType = recommendedGame,
            difficulty = difficulty,
            reason = reason,
            trend = trend,
            confidence = confidence
        )
    }
}