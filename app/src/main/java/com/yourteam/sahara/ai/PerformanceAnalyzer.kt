package com.yourteam.sahara.ai

import com.yourteam.sahara.model.GameResult
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class PerformanceAnalyzer {

    fun calculateOverallScore(recentGames: List<GameResult>): Float {
        if (recentGames.isEmpty()) return 0.5f

        val baseScores = recentGames.map { game ->
            // Accuracy: 50% weight
            val accScore = (game.accuracy / 100f) * 0.50f

            // Mistakes: 20% weight (max expected mistakes ~ totalPairs * 1.5)
            val maxMistakes = game.totalPairs * 1.5f
            val mistakeScore = max(0f, 1f - (game.mistakes / maxMistakes)) * 0.20f

            // Time: 20% weight (expected time ~ 15s per pair)
            val expectedTime = game.totalPairs * 15f
            val timeScore = max(0f, 1f - (game.completionTimeSeconds / (expectedTime * 1.5f))) * 0.20f

            accScore + mistakeScore + timeScore // Max achievable here is 0.90
        }

        val avgBaseScore = baseScores.average().toFloat()

        // Consistency: 10% weight based on variance of base scores
        val consistencyScore = if (baseScores.size > 1) {
            val variance = baseScores.map { (it - avgBaseScore).toDouble().pow(2.0) }.average()
            val stdDev = sqrt(variance).toFloat()
            // Lower stdDev = higher consistency. Cap stdDev penalty.
            max(0f, 1f - (stdDev * 4f)) * 0.10f
        } else {
            0.10f // Max consistency if only 1 game has been played
        }

        return (avgBaseScore + consistencyScore).coerceIn(0f, 1f)
    }
}