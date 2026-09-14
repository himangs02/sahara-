package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.GameResultDto
import com.yourteam.sahara.api.model.GameResultUpsertDto
import com.yourteam.sahara.model.GameResult

fun GameResult.toUpsertDto() = GameResultUpsertDto(
    id = syncId,
    gameType = gameType,
    difficulty = difficulty,
    totalPairs = totalPairs,
    matchedPairs = matchedPairs,
    mistakes = mistakes,
    completionTimeSeconds = completionTimeSeconds,
    accuracy = accuracy,
    completed = completed,
    occurredAt = Iso8601.fromMillis(timestamp)
)

fun GameResultDto.toDomain() = GameResult(
    patientId = patientId,
    syncId = id,
    gameType = gameType,
    difficulty = difficulty,
    totalPairs = totalPairs,
    matchedPairs = matchedPairs,
    mistakes = mistakes,
    completionTimeSeconds = completionTimeSeconds,
    accuracy = accuracy,
    completed = completed,
    timestamp = Iso8601.toMillis(occurredAt)
)
