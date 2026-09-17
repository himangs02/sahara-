package com.yourteam.sahara.ai

import com.yourteam.sahara.model.Difficulty

data class ActivityRecommendation(
    val gameType: String,
    val difficulty: Difficulty,
    val reason: String,
    val trend: ActivityTrend,
    val confidence: Float
)