package com.yourteam.sahara.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for backend/app/schemas/reminder.py. */

@Serializable
data class ReminderUpsertDto(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("reminder_type") val reminderType: String,
    @SerialName("minute_of_day") val minuteOfDay: Int,
    val enabled: Boolean
)

@Serializable
data class ReminderUpdateDto(
    val title: String? = null,
    val description: String? = null,
    @SerialName("reminder_type") val reminderType: String? = null,
    @SerialName("minute_of_day") val minuteOfDay: Int? = null,
    val enabled: Boolean? = null
)

@Serializable
data class ReminderDto(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    val title: String,
    val description: String,
    @SerialName("reminder_type") val reminderType: String,
    @SerialName("minute_of_day") val minuteOfDay: Int,
    val enabled: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
