package com.yourteam.sahara.ai

import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.UiText

data class AdaptiveRecommendation(
    val recommendedDifficulty: Difficulty,
    val performanceScore: Float,
    val reason: UiText,
    val confidence: Float,
    val recentAccuracy: Float = 0f,
    val recentMistakes: Int = 0,
    val recentSessionsCount: Int = 0
)