package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.GameResultDto
import com.yourteam.sahara.model.GameResult
import org.junit.Assert.assertEquals
import org.junit.Test

class GameResultMappingTest {

    @Test fun `domain result to upsert dto uses syncId as id and formats the timestamp`() {
        val result = GameResult(
            patientId = "patient-1",
            syncId = "result-1",
            gameType = "MEMORY_MATCH",
            difficulty = "EASY",
            totalPairs = 6,
            matchedPairs = 6,
            mistakes = 1,
            completionTimeSeconds = 45,
            accuracy = 92.5f,
            completed = true,
            timestamp = 1_757_836_800_000L // fixed instant, not "now" -- keeps this test deterministic
        )

        val dto = result.toUpsertDto()

        assertEquals("result-1", dto.id)
        assertEquals("MEMORY_MATCH", dto.gameType)
        assertEquals(6, dto.totalPairs)
        assertEquals(92.5f, dto.accuracy)
        assertEquals(Iso8601.fromMillis(1_757_836_800_000L), dto.occurredAt)
    }

    @Test fun `backend game result dto maps back to domain, parsing occurred_at`() {
        val dto = GameResultDto(
            id = "result-2",
            patientId = "patient-2",
            gameType = "ATTENTION_TAP",
            difficulty = "MEDIUM",
            totalPairs = 8,
            matchedPairs = 7,
            mistakes = 2,
            completionTimeSeconds = 60,
            accuracy = 80f,
            completed = true,
            occurredAt = "2026-09-14T12:00:00.000Z",
            syncedAt = "2026-09-14T12:00:05.000Z"
        )

        val result = dto.toDomain()

        assertEquals("result-2", result.syncId)
        assertEquals("patient-2", result.patientId)
        assertEquals("ATTENTION_TAP", result.gameType)
        assertEquals(Iso8601.toMillis("2026-09-14T12:00:00.000Z"), result.timestamp)
    }
}
