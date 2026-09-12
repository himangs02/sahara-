package com.yourteam.sahara.ai

import com.yourteam.sahara.model.Difficulty

data class AdaptiveRecommendation(
    val recommendedDifficulty: Difficulty,
    val performanceScore: Float,
    val reason: String,
    val confidence: Float,
    val recentAccuracy: Float = 0f,
    val recentMistakes: Int = 0,
    val recentSessionsCount: Int = 0
)