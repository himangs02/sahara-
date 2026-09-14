package com.yourteam.sahara.caregiver

import com.yourteam.sahara.R
import com.yourteam.sahara.ai.CaregiverInsightEngine
import com.yourteam.sahara.model.AlertKind
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.InsightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverInsightEngineTest {

    private val engine = CaregiverInsightEngine()

    private fun createResult(
        gameType: CognitiveActivityType,
        accuracy: Float,
        mistakes: Int,
        timeSeconds: Long,
        timestamp: Long = System.currentTimeMillis()
    ): GameResult {
        return GameResult(
            gameType = gameType.id,
            difficulty = Difficulty.EASY.name,
            totalPairs = 4,
            matchedPairs = 4,
            mistakes = mistakes,
            completionTimeSeconds = timeSeconds,
            accuracy = accuracy,
            completed = true,
            timestamp = timestamp
        )
    }

    @Test
    fun `Empty results return stable baseline insight`() {
        val insights = engine.generateInsights(emptyList())
        assertEquals(1, insights.size)
        assertEquals(InsightType.STABLE, insights.first().type)
    }

    @Test
    fun `Activity isolation - Memory decline does not flag Sequence as declined`() {
        val results = listOf(
            // Memory Match declining
            createResult(CognitiveActivityType.MEMORY_MATCH, 40f, 6, 80, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 45f, 5, 75, timestamp = 2000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 90f, 1, 30, timestamp = 1000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 95f, 0, 25, timestamp = 500),

            // Sequence Recall stable
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 85f, 1, 40, timestamp = 3000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 85f, 1, 40, timestamp = 2000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 85f, 1, 40, timestamp = 1000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 85f, 1, 40, timestamp = 500)
        )

        val insights = engine.generateInsights(results)
        val memoryInsight = insights.find { it.activityType == CognitiveActivityType.MEMORY_MATCH }
        val sequenceInsight = insights.find { it.activityType == CognitiveActivityType.SEQUENCE_RECALL }

        assertNotNull(memoryInsight)
        assertNotNull(sequenceInsight)

        assertEquals(InsightType.DECLINE, memoryInsight?.type)
        assertEquals(InsightType.STABLE, sequenceInsight?.type)
    }

    @Test
    fun `Performance decline is detected when recent accuracy drops`() {
        val results = listOf(
            createResult(CognitiveActivityType.ATTENTION_TAP, 40f, 5, 60, timestamp = 3000),
            createResult(CognitiveActivityType.ATTENTION_TAP, 45f, 4, 55, timestamp = 2000),
            createResult(CognitiveActivityType.ATTENTION_TAP, 85f, 1, 25, timestamp = 1000),
            createResult(CognitiveActivityType.ATTENTION_TAP, 90f, 0, 20, timestamp = 500)
        )

        val insights = engine.generateInsights(results)
        val attentionInsight = insights.find { it.activityType == CognitiveActivityType.ATTENTION_TAP }

        assertEquals(InsightType.DECLINE, attentionInsight?.type)
    }

    @Test
    fun `Stable performance is detected when accuracy remains steady`() {
        val results = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, 80f, 2, 40, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 82f, 2, 38, timestamp = 2000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 81f, 2, 39, timestamp = 1000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 80f, 2, 40, timestamp = 500)
        )

        val insights = engine.generateInsights(results)
        val memoryInsight = insights.find { it.activityType == CognitiveActivityType.MEMORY_MATCH }

        assertEquals(InsightType.STABLE, memoryInsight?.type)
    }

    @Test
    fun `Consecutive low performance generates engagement alert`() {
        val results = listOf(
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 40f, 6, 90, timestamp = 3000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 45f, 5, 85, timestamp = 2000),
            createResult(CognitiveActivityType.SEQUENCE_RECALL, 50f, 5, 80, timestamp = 1000)
        )

        val alerts = engine.generateAlerts(results)
        val alert = alerts.find { it.activityType == CognitiveActivityType.SEQUENCE_RECALL }

        assertNotNull(alert)
        assertEquals(AlertKind.LOW_PERFORMANCE, alert?.kind)
        assertEquals(R.string.alert_low_title, alert?.title?.id)
    }

    @Test
    fun `Why change breakdown accurately compares recent vs previous metrics`() {
        val results = listOf(
            createResult(CognitiveActivityType.MEMORY_MATCH, 60f, 4, 60, timestamp = 3000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 60f, 4, 60, timestamp = 2000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 60f, 4, 60, timestamp = 1000),
            createResult(CognitiveActivityType.MEMORY_MATCH, 90f, 1, 30, timestamp = 500),
            createResult(CognitiveActivityType.MEMORY_MATCH, 90f, 1, 30, timestamp = 400),
            createResult(CognitiveActivityType.MEMORY_MATCH, 90f, 1, 30, timestamp = 300)
        )

        val breakdown = engine.getWhyChangeBreakdown(results, CognitiveActivityType.MEMORY_MATCH)

        assertEquals(60f, breakdown.recentAccuracy, 0.1f)
        assertEquals(90f, breakdown.previousAccuracy, 0.1f)
        assertEquals(R.string.interpretation_lower, breakdown.interpretation.id)
    }
}
