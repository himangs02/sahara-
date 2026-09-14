package com.yourteam.sahara.model

enum class InsightType {
    STABLE,
    IMPROVING,
    DECLINE,
    ATTENTION_NEEDED
}

data class CaregiverInsight(
    val activityType: CognitiveActivityType?,
    val title: UiText,
    val summary: UiText,
    val observations: List<UiText>,
    val type: InsightType,
    val timestamp: Long = System.currentTimeMillis()
)
