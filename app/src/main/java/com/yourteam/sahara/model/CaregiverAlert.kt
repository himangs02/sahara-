package com.yourteam.sahara.model

import com.yourteam.sahara.R

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class AlertKind {
    LOW_PERFORMANCE,
    INACTIVITY
}

data class CaregiverAlert(
    val id: String,
    val kind: AlertKind,
    val activityType: CognitiveActivityType?,
    val severity: AlertSeverity,
    val reviewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Derived from the kind so stored alerts display in the current UI language.
    val title: UiText
        get() = when (kind) {
            AlertKind.LOW_PERFORMANCE -> UiText(R.string.alert_low_title, activityName())
            AlertKind.INACTIVITY -> UiText(R.string.alert_inactivity_title)
        }

    val message: UiText
        get() = when (kind) {
            AlertKind.LOW_PERFORMANCE -> UiText(R.string.alert_low_message, activityName())
            AlertKind.INACTIVITY -> UiText(R.string.alert_inactivity_message)
        }

    private fun activityName() = UiText((activityType ?: CognitiveActivityType.MEMORY_MATCH).titleRes)
}
