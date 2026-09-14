package com.yourteam.sahara.notifications

import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import java.util.Calendar
import java.util.TimeZone

/** A reminder has at most one alarm of each kind; scheduling again replaces it. */
enum class AlarmKind { DAILY, SNOOZE }

/** Where alarms are actually set. The Android implementation uses AlarmManager. */
interface AlarmGateway {
    fun schedule(reminderId: String, kind: AlarmKind, triggerAtMillis: Long)
    fun cancel(reminderId: String, kind: AlarmKind)
}

/**
 * Decides when each reminder should fire. Only the next occurrence is ever scheduled; when it fires,
 * the following day is scheduled, so there is no long-running service and no duplicate alarms.
 * Times are computed with a Calendar in the device time zone, which handles month ends and
 * daylight-saving shifts.
 */
class ReminderScheduler(
    private val alarms: AlarmGateway,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: () -> TimeZone = TimeZone::getDefault
) {

    fun currentMillis(): Long = now()

    fun localClock(): LocalClock = LocalClock.at(now(), timeZone())

    /** Brings a reminder's alarms in line with its stored state. Safe to call any number of times. */
    fun sync(reminder: Reminder) {
        if (!reminder.enabled) {
            cancelAll(reminder.id)
            return
        }
        val nowMillis = now()
        val zone = timeZone()
        if (reminder.isCompletedOn(LocalClock.at(nowMillis, zone))) alarms.cancel(reminder.id, AlarmKind.SNOOZE)
        alarms.schedule(reminder.id, AlarmKind.DAILY, nextDailyTrigger(reminder, nowMillis, zone))
    }

    fun rescheduleAll(reminders: List<Reminder>) = reminders.forEach(::sync)

    fun cancelAll(reminderId: String) {
        alarms.cancel(reminderId, AlarmKind.DAILY)
        alarms.cancel(reminderId, AlarmKind.SNOOZE)
    }

    /** Schedules a one-off repeat [SNOOZE_MINUTES] from now. Returns its time, or null if nothing is due. */
    fun snooze(reminder: Reminder): Long? {
        if (!reminder.enabled || reminder.isCompletedOn(localClock())) return null
        val triggerAt = now() + SNOOZE_MINUTES * 60_000L
        alarms.schedule(reminder.id, AlarmKind.SNOOZE, triggerAt)
        return triggerAt
    }

    /**
     * Whether a fired alarm should show a notification. Nothing is shown for disabled reminders, for
     * reminders already done today, or for a daily alarm delivered after the reminder already counts
     * as missed (for example after the phone was switched off).
     */
    fun shouldNotify(reminder: Reminder, kind: AlarmKind): Boolean {
        val clock = localClock()
        if (!reminder.enabled || reminder.isCompletedOn(clock)) return false
        if (kind == AlarmKind.SNOOZE) return true
        return clock.minuteOfDay in (reminder.minuteOfDay - EARLY_TOLERANCE_MINUTES)..(reminder.minuteOfDay + Reminder.MISSED_AFTER_MINUTES)
    }

    companion object {
        const val SNOOZE_MINUTES = 10
        private const val EARLY_TOLERANCE_MINUTES = 1

        /**
         * The next time strictly after [nowMillis] when the reminder is due. If it was already
         * completed today, today's time is skipped.
         */
        fun nextDailyTrigger(reminder: Reminder, nowMillis: Long, timeZone: TimeZone): Long {
            val completedToday = reminder.isCompletedOn(LocalClock.at(nowMillis, timeZone))
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
            fun atReminderTime() = calendar.apply {
                set(Calendar.HOUR_OF_DAY, reminder.minuteOfDay / 60)
                set(Calendar.MINUTE, reminder.minuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val today = atReminderTime()
            if (today > nowMillis && !completedToday) return today
            // Add a calendar day (not 24 hours) and set the time again so daylight-saving days stay correct.
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            return atReminderTime()
        }
    }
}
