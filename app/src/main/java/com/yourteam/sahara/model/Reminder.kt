package com.yourteam.sahara.model

import com.yourteam.sahara.R
import java.util.TimeZone
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

/**
 * A daily reminder. Completion is stored as the local calendar day it was last marked done,
 * so "done today" is derived from the date and a new day starts incomplete without any worker.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val patientId: String = com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID,
    val title: String,
    val description: String = "",
    val type: ReminderType = ReminderType.GENERAL,
    /** Minutes after local midnight, 0..1439. */
    val minuteOfDay: Int,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Local epoch day of the last completion, or [NOT_COMPLETED]. */
    val lastCompletedEpochDay: Long = NOT_COMPLETED
) {
    init {
        require(minuteOfDay in 0 until 24 * 60) { "minuteOfDay out of range: $minuteOfDay" }
    }

    fun isCompletedOn(clock: LocalClock) = lastCompletedEpochDay == clock.epochDay

    fun statusAt(clock: LocalClock): ReminderStatus = when {
        isCompletedOn(clock) -> ReminderStatus.COMPLETED
        clock.minuteOfDay > minuteOfDay + MISSED_AFTER_MINUTES -> ReminderStatus.MISSED
        else -> ReminderStatus.UPCOMING
    }

    fun withCompleted(done: Boolean, clock: LocalClock) = when {
        done -> copy(lastCompletedEpochDay = clock.epochDay)
        // Undo only today's completion; an older date already reads as not done.
        isCompletedOn(clock) -> copy(lastCompletedEpochDay = NOT_COMPLETED)
        else -> this
    }

    companion object {
        const val NOT_COMPLETED = -1L
        const val MISSED_AFTER_MINUTES = 60
    }
}

/** Reminders shipped with the app. Fixed ids let seeding skip ones that already exist. */
object BuiltInReminders {
    fun all(now: Long = System.currentTimeMillis()) = listOf(
        Reminder(id = "rem_001", title = "Morning Medicine", type = ReminderType.MEDICINE, minuteOfDay = 8 * 60, createdAt = now),
        Reminder(id = "rem_002", title = "Hydration", type = ReminderType.HYDRATION, minuteOfDay = 11 * 60, createdAt = now),
        Reminder(id = "rem_003", title = "Cognitive Activity", type = ReminderType.COGNITIVE_ACTIVITY, minuteOfDay = 16 * 60, createdAt = now),
        Reminder(id = "rem_004", title = "Doctor Appointment", type = ReminderType.APPOINTMENT, minuteOfDay = 18 * 60, createdAt = now)
    )

    private val originalTitles = all(0).associate { it.id to it.title }

    /** True while a built-in reminder still has its shipped English title, so the UI may translate it. */
    fun hasOriginalTitle(reminder: Reminder) = originalTitles[reminder.id] == reminder.title

    private val titleRes = mapOf(
        "rem_001" to R.string.morning_medicine,
        "rem_002" to R.string.hydration,
        "rem_003" to R.string.reminder_cognitive_activity,
        "rem_004" to R.string.reminder_doctor_appointment
    )

    /** The translated title for an unedited built-in reminder; null means show the stored title as entered. */
    fun localizedTitleRes(reminder: Reminder): Int? = titleRes[reminder.id]?.takeIf { hasOriginalTitle(reminder) }
}

/** The current day and time in the device's time zone, without java.time (minSdk 24). */
data class LocalClock(val epochDay: Long, val minuteOfDay: Int) {
    companion object {
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 24 * 60 * MINUTE_MS

        fun at(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): LocalClock {
            val local = millis + timeZone.getOffset(millis)
            return LocalClock(
                epochDay = Math.floorDiv(local, DAY_MS),
                minuteOfDay = (Math.floorMod(local, DAY_MS) / MINUTE_MS).toInt()
            )
        }

        fun now() = at(System.currentTimeMillis())
    }
}

/** Reminder with its status for the current day. */
data class TodayReminder(val reminder: Reminder, val status: ReminderStatus)

/** Ordered by time; the elderly view passes includeDisabled = false. */
fun List<Reminder>.forToday(clock: LocalClock, includeDisabled: Boolean = false): List<TodayReminder> =
    filter { includeDisabled || it.enabled }
        .sortedWith(compareBy({ it.minuteOfDay }, { it.createdAt }))
        .map { TodayReminder(it, it.statusAt(clock)) }
