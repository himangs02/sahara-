package com.yourteam.sahara.model

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class CaregiverAlert(
    val id: String,
    val title: String,
    val message: String,
    val activityType: CognitiveActivityType?,
    val severity: AlertSeverity,
    val reviewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
