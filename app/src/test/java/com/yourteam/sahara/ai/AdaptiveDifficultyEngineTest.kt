package com.yourteam.sahara.ai

import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDifficultyEngineTest {

    private val engine = AdaptiveDifficultyEngine()

    private fun createResult(
        gameType: CognitiveActivityType = CognitiveActivityType.MEMORY_MATCH,
        difficulty: Difficulty,
        accuracy: Float,
        mistakes: Int,
        time: Long,
        completed: Boolean = true,
        timestamp: Long = System.currentTimeMillis()
    ): GameResult {
        return GameResult(
            gameType = gameType.id,
            difficulty = difficulty.name,
            totalPairs = difficulty.pairs,
            matchedPairs = difficulty.pairs,
            mistakes = mistakes,
            completionTimeSeconds = time,
            accuracy = accuracy,
            completed = completed,
            timestamp = timestamp
        )
    }

    @Test
    fun `No history returns EASY`() {
        val recommendation = engine.analyzeAndRecommend(emptyList(), CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.EASY, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Memory history does not affect Sequence Recall`() {
        // Strong memory match history
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 30, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 30, timestamp = 2000)
        )

        // Request recommendation for Sequence Recall
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.SEQUENCE_RECALL)
        
        // Sequence Recall has no history, so it must return EASY!
        assertEquals(Difficulty.EASY, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Strong Attention history promotes Attention difficulty`() {
        val history = listOf(
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.EASY, 100f, 0, 20, timestamp = 3000),
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.EASY, 100f, 0, 20, timestamp = 2000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.ATTENTION_TAP)
        assertEquals(Difficulty.MEDIUM, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Weak Attention history reduces Attention difficulty`() {
        val history = listOf(
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.HARD, 20f, 10, 100, timestamp = 3000),
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.HARD, 20f, 10, 100, timestamp = 2000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.ATTENTION_TAP)
        assertEquals(Difficulty.MEDIUM, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Mixed activity history does not corrupt recommendations`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 20, timestamp = 4000),
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.EASY, 100f, 0, 15, timestamp = 3000),
            createResult(CognitiveActivityType.ATTENTION_TAP, Difficulty.EASY, 100f, 0, 15, timestamp = 2000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, Difficulty.EASY, 40f, 5, 80, timestamp = 1000)
        )

        val memoryRec = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        val attentionRec = engine.analyzeAndRecommend(history, CognitiveActivityType.ATTENTION_TAP)
        val sequenceRec = engine.analyzeAndRecommend(history, CognitiveActivityType.SEQUENCE_RECALL)

        assertEquals(Difficulty.HARD, memoryRec.recommendedDifficulty)
        assertEquals(Difficulty.MEDIUM, attentionRec.recommendedDifficulty)
        assertEquals(Difficulty.EASY, sequenceRec.recommendedDifficulty)
    }

    @Test
    fun `New user (one game) maintains current difficulty`() {
        val history = listOf(createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 85f, 2, 45))
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.EASY, recommendation.recommendedDifficulty)
    }

    @Test
    fun `One bad session does not immediately decrease difficulty (hysteresis)`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.MEDIUM, 10f, 20, 200, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.MEDIUM, 90f, 1, 40, timestamp = 2000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.MEDIUM, 85f, 2, 45, timestamp = 1000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.MEDIUM, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Never exceed HARD`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 30, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 30, timestamp = 2000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.HARD, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Never go below EASY`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 10f, 20, 200, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 10f, 20, 200, timestamp = 2000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.EASY, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Incomplete games are ignored`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.HARD, 100f, 0, 30, completed = false, timestamp = 4000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 30, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 30, timestamp = 2000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertEquals(Difficulty.MEDIUM, recommendation.recommendedDifficulty)
    }

    @Test
    fun `Performance score remains between 0 and 1`() {
        val history = listOf(createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 10, timestamp = 1000))
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertTrue(recommendation.performanceScore in 0.0f..1.0f)
    }

    @Test
    fun `Confidence remains between 0 and 1`() {
        val history = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 30, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 30, timestamp = 2000),
            createResult(CognitiveActivityType.MEMORY_MATCH, Difficulty.EASY, 100f, 0, 30, timestamp = 1000)
        )
        val recommendation = engine.analyzeAndRecommend(history, CognitiveActivityType.MEMORY_MATCH)
        assertTrue(recommendation.confidence in 0.0f..1.0f)
    }
}
