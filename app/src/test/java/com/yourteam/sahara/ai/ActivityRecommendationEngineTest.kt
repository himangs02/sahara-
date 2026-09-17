package com.yourteam.sahara.ai

import com.yourteam.sahara.model.GameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
private fun result(
    gameType: String,
    accuracy: Float
): GameResult {
    return GameResult(
        gameType = gameType,
        difficulty = "EASY",
        totalPairs = 4,
        matchedPairs = 4,
        mistakes = 0,
        completionTimeSeconds = 30,
        accuracy = accuracy,
        completed = true,
        timestamp = System.currentTimeMillis()
    )
}