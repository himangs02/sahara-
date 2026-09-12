package com.yourteam.sahara.model

enum class InsightType {
    STABLE,
    IMPROVING,
    DECLINE,
    ATTENTION_NEEDED
}

data class CaregiverInsight(
    val activityType: CognitiveActivityType?,
    val title: String,
    val summary: String,
    val observations: List<String>,
    val type: InsightType,
    val timestamp: Long = System.currentTimeMillis()
)
