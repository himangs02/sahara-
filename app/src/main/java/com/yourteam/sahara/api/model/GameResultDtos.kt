package com.yourteam.sahara.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for backend/app/schemas/game_result.py. Timestamps are ISO-8601 UTC
 * strings on the wire; see [com.yourteam.sahara.api.Iso8601] for the millis <-> string
 * conversion (minSdk 24 has no java.time without core library desugaring, which this
 * project doesn't enable, so a small hand-rolled formatter is used instead). */

@Serializable
data class GameResultUpsertDto(
    val id: String,
    @SerialName("game_type") val gameType: String,
    val difficulty: String,
    @SerialName("total_pairs") val totalPairs: Int,
    @SerialName("matched_pairs") val matchedPairs: Int,
    val mistakes: Int,
    @SerialName("completion_time_seconds") val completionTimeSeconds: Long,
    val accuracy: Float,
    val completed: Boolean,
    @SerialName("occurred_at") val occurredAt: String
)

@Serializable
data class GameResultDto(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("game_type") val gameType: String,
    val difficulty: String,
    @SerialName("total_pairs") val totalPairs: Int,
    @SerialName("matched_pairs") val matchedPairs: Int,
    val mistakes: Int,
    @SerialName("completion_time_seconds") val completionTimeSeconds: Long,
    val accuracy: Float,
    val completed: Boolean,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("synced_at") val syncedAt: String
)
