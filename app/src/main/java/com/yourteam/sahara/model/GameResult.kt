package com.yourteam.sahara.model

import java.util.UUID

data class GameResult(
    val syncId: String = UUID.randomUUID().toString(),
    val gameType: String = "Memory Match",
    val difficulty: String,
    val totalPairs: Int,
    val matchedPairs: Int,
    val mistakes: Int,
    val completionTimeSeconds: Long,
    val accuracy: Float,
    val completed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
