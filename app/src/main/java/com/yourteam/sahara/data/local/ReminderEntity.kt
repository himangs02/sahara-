package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.ReminderType

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val patientId: String,
    val title: String,
    val type: String,
    val scheduledTime: String,
    val timeMillis: Long,
    val status: String,
    val enabled: Boolean
)

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        patientId = patientId,
        title = title,
        type = try { ReminderType.valueOf(type) } catch (_: Exception) { ReminderType.GENERAL },
        scheduledTime = scheduledTime,
        timeMillis = timeMillis,
        status = try { ReminderStatus.valueOf(status) } catch (_: Exception) { ReminderStatus.UPCOMING },
        enabled = enabled
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        patientId = patientId,
        title = title,
        type = type.name,
        scheduledTime = scheduledTime,
        timeMillis = timeMillis,
        status = status.name,
        enabled = enabled
    )
}
