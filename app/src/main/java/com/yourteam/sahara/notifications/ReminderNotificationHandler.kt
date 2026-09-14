package com.yourteam.sahara.notifications

import com.yourteam.sahara.data.repository.ReminderChangeListener
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.Reminder

/** What the handler needs from the notification system; the Android implementation is [ReminderNotifier]. */
interface ReminderNotificationDisplay {
    fun show(reminder: Reminder)
    fun showSnoozed(reminder: Reminder, untilMillis: Long)
    fun cancel(reminderId: String)
}

/** Keeps alarms and any visible notification in line with every stored reminder change. */
class ReminderNotificationSync(
    private val scheduler: ReminderScheduler,
    private val display: ReminderNotificationDisplay
) : ReminderChangeListener {

    override fun onReminderSaved(reminder: Reminder) {
        scheduler.sync(reminder)
        // Done in the app or switched off: the notification is no longer relevant.
        if (!reminder.enabled || reminder.isCompletedOn(scheduler.localClock())) display.cancel(reminder.id)
    }

    override fun onReminderDeleted(reminder: Reminder) {
        scheduler.cancelAll(reminder.id)
        display.cancel(reminder.id)
    }
}

/** Responds to fired alarms, notification actions and system time events. Receivers only delegate here. */
class ReminderNotificationHandler(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val display: ReminderNotificationDisplay
) {

    suspend fun onAlarm(reminderId: String, kind: AlarmKind) {
        val reminder = repository.getReminder(reminderId)
        if (reminder == null) {
            // Deleted since the alarm was set.
            scheduler.cancelAll(reminderId)
            display.cancel(reminderId)
            return
        }
        if (scheduler.shouldNotify(reminder, kind)) display.show(reminder)
        // Sets up the next day (or cancels, if the reminder is now disabled).
        if (kind == AlarmKind.DAILY) scheduler.sync(reminder)
    }

    /** Marks the reminder done for today through the repository, which also clears the snooze and notification. */
    suspend fun onDone(reminderId: String) {
        if (repository.getReminder(reminderId) == null) {
            display.cancel(reminderId)
            return
        }
        repository.setCompleted(reminderId, true, scheduler.localClock())
    }

    suspend fun onSnooze(reminderId: String) {
        val reminder = repository.getReminder(reminderId)
        val until = reminder?.let { scheduler.snooze(it) }
        if (reminder == null || until == null) display.cancel(reminderId) else display.showSnoozed(reminder, until)
    }

    /** After app start, reboot, time or time-zone change. */
    suspend fun rescheduleAll() = scheduler.rescheduleAll(repository.getAllReminders())
}
