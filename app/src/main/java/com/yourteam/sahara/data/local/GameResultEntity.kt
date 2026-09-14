package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.GameResult
import java.util.UUID

@Entity(tableName = "game_results")
data class GameResultEntity(
    @androidx.room.ColumnInfo(defaultValue = "'30e140b1-88e7-419e-8b7d-6063ee613c01'")
    val patientId: String = com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val gameType: String,
    val difficulty: String,
    val totalPairs: Int,
    val matchedPairs: Int,
    val mistakes: Int,
    val completionTimeSeconds: Long,
    val accuracy: Float,
    val completed: Boolean,
    val timestamp: Long
)

fun GameResultEntity.toDomain(): GameResult {
    return GameResult(
        patientId = patientId,
        syncId = syncId,
        gameType = gameType,
        difficulty = difficulty,
        totalPairs = totalPairs,
        matchedPairs = matchedPairs,
        mistakes = mistakes,
        completionTimeSeconds = completionTimeSeconds,
        accuracy = accuracy,
        completed = completed,
        timestamp = timestamp
    )
}

fun GameResult.toEntity(): GameResultEntity {
    return GameResultEntity(
        patientId = patientId,
        syncId = syncId,
        gameType = gameType,
        difficulty = difficulty,
        totalPairs = totalPairs,
        matchedPairs = matchedPairs,
        mistakes = mistakes,
        completionTimeSeconds = completionTimeSeconds,
        accuracy = accuracy,
        completed = completed,
        timestamp = timestamp
    )
}
