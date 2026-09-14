package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderType

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey
    val id: String,
    val patientId: String,
    val title: String,
    val description: String,
    val type: String,
    val minuteOfDay: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val lastCompletedEpochDay: Long
)

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        patientId = patientId,
        title = title,
        description = description,
        type = try { ReminderType.valueOf(type) } catch (_: Exception) { ReminderType.GENERAL },
        minuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1),
        enabled = enabled,
        createdAt = createdAt,
        lastCompletedEpochDay = lastCompletedEpochDay
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        patientId = patientId,
        title = title,
        description = description,
        type = type.name,
        minuteOfDay = minuteOfDay,
        enabled = enabled,
        createdAt = createdAt,
        lastCompletedEpochDay = lastCompletedEpochDay
    )
}

private val legacyTime = Regex("""^\s*(\d{1,2}):(\d{2})\s*([AaPp])?\.?\s*[Mm]?\.?\s*$""")

/** Parses the version-4 display times ("8:00 AM", "18:30"); unparseable values fall back to 9:00. */
fun parseLegacyReminderTime(text: String?): Int {
    val match = legacyTime.matchEntire(text ?: "") ?: return 9 * 60
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    when (match.groupValues[3].lowercase()) {
        "a" -> if (hour == 12) hour = 0
        "p" -> if (hour != 12) hour += 12
    }
    if (hour !in 0..23 || minute !in 0..59) return 9 * 60
    return hour * 60 + minute
}
