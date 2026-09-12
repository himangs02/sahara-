package com.yourteam.sahara.model

import java.util.UUID

enum class ReminderType {
    MEDICINE,
    HYDRATION,
    COGNITIVE_ACTIVITY,
    APPOINTMENT,
    GENERAL
}

enum class ReminderStatus {
    UPCOMING,
    COMPLETED,
    MISSED
}

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val patientId: String = "patient_001",
    val title: String,
    val type: ReminderType = ReminderType.GENERAL,
    val scheduledTime: String,
    val timeMillis: Long = System.currentTimeMillis(),
    val status: ReminderStatus = ReminderStatus.UPCOMING,
    val enabled: Boolean = true
)
