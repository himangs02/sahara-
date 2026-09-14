package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.ReminderDto
import com.yourteam.sahara.api.model.ReminderUpsertDto
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderType

/** Only the reminder *definition* is synced -- device-specific state
 * ([Reminder.lastCompletedEpochDay], an in-progress snooze) never leaves the device. */
fun Reminder.toUpsertDto() = ReminderUpsertDto(
    id = id,
    title = title,
    description = description,
    reminderType = type.name,
    minuteOfDay = minuteOfDay,
    enabled = enabled
)

fun ReminderDto.toDomain() = Reminder(
    id = id,
    patientId = patientId,
    title = title,
    description = description,
    type = try { ReminderType.valueOf(reminderType) } catch (_: Exception) { ReminderType.GENERAL },
    minuteOfDay = minuteOfDay,
    enabled = enabled,
    createdAt = Iso8601.toMillis(createdAt)
    // lastCompletedEpochDay intentionally left at its default (NOT_COMPLETED): completion
    // is device-specific state, never pulled from the server.
)
