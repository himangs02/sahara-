package com.yourteam.sahara.ai

import com.yourteam.sahara.R
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.UiText

class AdaptiveDifficultyEngine(private val analyzer: PerformanceAnalyzer = PerformanceAnalyzer()) {

    fun analyzeAndRecommend(
        history: List<GameResult>,
        activityType: CognitiveActivityType = CognitiveActivityType.MEMORY_MATCH
    ): AdaptiveRecommendation {
        val name = UiText(activityType.titleRes)
        // Filter out incomplete games and keep only results for the requested activity type
        val completed = history
            .filter { 
                it.completed && (
                    it.gameType.equals(activityType.id, ignoreCase = true) || 
                    it.gameType.equals(activityType.displayName, ignoreCase = true)
                ) 
            }
            .sortedByDescending { it.timestamp }

        if (completed.isEmpty()) {
            return AdaptiveRecommendation(
                recommendedDifficulty = Difficulty.EASY,
                performanceScore = 0.5f,
                reason = UiText(R.string.reason_no_history, name),
                confidence = 0.0f
            )
        }

        // Focus on the 3 most recent sessions for activity-specific adaptive difficulty
        val recent = completed.take(3)
        val score = analyzer.calculateOverallScore(recent)
        
        // Calculate recent stats for the "Why" dialog
        val recentAccuracy = recent.map { it.accuracy }.average().toFloat()
        val recentMistakes = recent.sumOf { it.mistakes }
        val recentSessionsCount = recent.size

        // We use the most recent difficulty as our baseline
        val currentDifficultyStr = recent.first().difficulty
        val currentDifficulty = try {
            Difficulty.valueOf(currentDifficultyStr)
        } catch (_: Exception) {
            Difficulty.EASY
        }
        
        var recommended = currentDifficulty
        var reason = UiText(R.string.reason_stable, name)
        val confidence = minOf(1.0f, recent.size * 0.33f)

        // Hysteresis rule: Only promote or demote if the user has played at least 2 games
        // at the CURRENT difficulty for THIS activity.
        val recentSameDifficulty = recent.filter { it.difficulty == currentDifficulty.name }

        if (recentSameDifficulty.size >= 2) {
            val recentAvgAcc = recentSameDifficulty.map { it.accuracy }.average()
            
            // STRONG performance criteria
            if (score >= 0.75f && recentAvgAcc >= 80.0) {
                if (currentDifficulty == Difficulty.EASY) {
                    recommended = Difficulty.MEDIUM
                    reason = UiText(R.string.reason_promote_medium, name)
                } else if (currentDifficulty == Difficulty.MEDIUM) {
                    recommended = Difficulty.HARD
                    reason = UiText(R.string.reason_promote_hard, name)
                } else {
                    reason = UiText(R.string.reason_at_max, name)
                }
            } 
            // WEAK performance criteria
            else if (score <= 0.45f && recentAvgAcc <= 50.0) {
                if (currentDifficulty == Difficulty.HARD) {
                    recommended = Difficulty.MEDIUM
                    reason = UiText(R.string.reason_adjust_down, name)
                } else if (currentDifficulty == Difficulty.MEDIUM) {
                    recommended = Difficulty.EASY
                    reason = UiText(R.string.reason_adjust_down, name)
                } else {
                    reason = UiText(R.string.reason_keep_practicing, name)
                }
            }
        } else if (recent.size == 1) {
            reason = UiText(R.string.reason_first_session, name)
        } else {
            reason = UiText(R.string.reason_gathering, name)
        }

        return AdaptiveRecommendation(
            recommendedDifficulty = recommended,
            performanceScore = score,
            reason = reason,
            confidence = confidence,
            recentAccuracy = recentAccuracy,
            recentMistakes = recentMistakes,
            recentSessionsCount = recentSessionsCount
        )
    }
}
