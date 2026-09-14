package com.yourteam.sahara.reminder

import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.notifications.AlarmGateway
import com.yourteam.sahara.notifications.AlarmKind
import com.yourteam.sahara.notifications.NotificationAccess
import com.yourteam.sahara.notifications.NotificationPermission
import com.yourteam.sahara.notifications.ReminderNotificationDisplay
import com.yourteam.sahara.notifications.ReminderNotificationHandler
import com.yourteam.sahara.notifications.ReminderNotificationSync
import com.yourteam.sahara.notifications.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Mirrors AlarmManager + PendingIntent semantics: one alarm per reminder and kind, replaced when set again. */
private class FakeAlarmGateway : AlarmGateway {
    val alarms = mutableMapOf<Pair<String, AlarmKind>, Long>()
    var scheduleCalls = 0

    override fun schedule(reminderId: String, kind: AlarmKind, triggerAtMillis: Long) {
        scheduleCalls++
        alarms[reminderId to kind] = triggerAtMillis
    }

    override fun cancel(reminderId: String, kind: AlarmKind) {
        alarms.remove(reminderId to kind)
    }

    fun daily(id: String) = alarms[id to AlarmKind.DAILY]
    fun snooze(id: String) = alarms[id to AlarmKind.SNOOZE]
}

private class FakeDisplay : ReminderNotificationDisplay {
    /** Reminder id to what is currently visible: "shown" or "snoozed". */
    val visible = mutableMapOf<String, String>()
    var showCount = 0

    override fun show(reminder: Reminder) {
        showCount++
        visible[reminder.id] = "shown"
    }

    override fun showSnoozed(reminder: Reminder, untilMillis: Long) {
        visible[reminder.id] = "snoozed"
    }

    override fun cancel(reminderId: String) {
        visible.remove(reminderId)
    }
}

class ReminderNotificationTest {

    private var zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private var nowMillis = at(2026, Calendar.SEPTEMBER, 13, 7, 0)

    private val gateway = FakeAlarmGateway()
    private val display = FakeDisplay()
    private val scheduler = ReminderScheduler(gateway, now = { nowMillis }, timeZone = { zone })
    private val dao = FakeReminderDao()
    private val repository = ReminderRepository(dao, Dispatchers.Unconfined, ReminderNotificationSync(scheduler, display))
    private val handler = ReminderNotificationHandler(repository, scheduler, display)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, tz: TimeZone = zone): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun medicine(minuteOfDay: Int = 8 * 60) = Reminder(id = "med", title = "Tablet after breakfast", minuteOfDay = minuteOfDay)

    private fun stored(id: String) = runBlocking { repository.getReminder(id) }

    @Test
    fun `Enabled reminder is scheduled for today when its time is still ahead`() = runBlocking {
        repository.insertReminder(medicine())
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 0), gateway.daily("med"))
        assertNull(gateway.snooze("med"))
    }

    @Test
    fun `Reminder whose time has passed is scheduled for tomorrow`() = runBlocking {
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 9, 30)
        repository.insertReminder(medicine())
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))
    }

    @Test
    fun `Disabling cancels the alarm, snooze and notification, and enabling restores it`() = runBlocking {
        repository.insertReminder(medicine())
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 0)
        handler.onAlarm("med", AlarmKind.DAILY)
        handler.onSnooze("med")
        assertEquals("snoozed", display.visible["med"])

        repository.setEnabled("med", false)
        assertTrue(gateway.alarms.isEmpty())
        assertFalse("med" in display.visible)

        repository.setEnabled("med", true)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))
    }

    @Test
    fun `Editing the time replaces the alarm instead of adding one`() = runBlocking {
        repository.insertReminder(medicine())
        repository.insertReminder(stored("med")!!.copy(minuteOfDay = 9 * 60 + 30))

        assertEquals(1, gateway.alarms.size)
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 9, 30), gateway.daily("med"))
    }

    @Test
    fun `Deleting cancels alarms and the visible notification`() = runBlocking {
        repository.insertReminder(medicine())
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 0)
        handler.onAlarm("med", AlarmKind.DAILY)
        handler.onSnooze("med")

        repository.deleteReminder(stored("med")!!)
        assertTrue(gateway.alarms.isEmpty())
        assertTrue(display.visible.isEmpty())

        // An alarm that was already in flight for the deleted reminder shows nothing.
        handler.onAlarm("med", AlarmKind.DAILY)
        assertEquals(1, display.showCount)
    }

    @Test
    fun `Rescheduling repeatedly never creates duplicate alarms`() = runBlocking {
        repository.insertReminder(medicine())
        repository.insertReminder(Reminder(id = "water", title = "Water", minuteOfDay = 11 * 60))

        repeat(3) { handler.rescheduleAll() }
        scheduler.sync(stored("med")!!)

        assertEquals(setOf("med" to AlarmKind.DAILY, "water" to AlarmKind.DAILY), gateway.alarms.keys)
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 0), gateway.daily("med"))
    }

    @Test
    fun `Snooze repeats only that reminder once, ten minutes later`() = runBlocking {
        repository.insertReminder(medicine())
        repository.insertReminder(Reminder(id = "water", title = "Water", minuteOfDay = 8 * 60))
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 0)
        handler.onAlarm("med", AlarmKind.DAILY)

        handler.onSnooze("med")
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 10), gateway.snooze("med"))
        assertNull(gateway.snooze("water"))
        assertEquals("snoozed", display.visible["med"])

        // Snoozing again moves the single snooze alarm rather than adding a second.
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 10)
        handler.onAlarm("med", AlarmKind.SNOOZE)
        assertEquals("shown", display.visible["med"])
        handler.onSnooze("med")
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 20), gateway.snooze("med"))
        assertEquals(1, gateway.alarms.keys.count { it.second == AlarmKind.SNOOZE })
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))
    }

    @Test
    fun `Done after snooze completes today, clears the snooze and moves to tomorrow`() = runBlocking {
        repository.insertReminder(medicine())
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 0)
        handler.onAlarm("med", AlarmKind.DAILY)
        handler.onSnooze("med")

        handler.onDone("med")
        handler.onDone("med")

        val today = LocalClock.at(nowMillis, zone)
        assertEquals(today.epochDay, stored("med")!!.lastCompletedEpochDay)
        assertTrue(stored("med")!!.isCompletedOn(today))
        assertNull(gateway.snooze("med"))
        assertFalse("med" in display.visible)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))

        // A snooze alarm that was already in flight shows nothing once done.
        handler.onAlarm("med", AlarmKind.SNOOZE)
        assertFalse("med" in display.visible)
    }

    @Test
    fun `Done in the app before the reminder time skips today's notification`() = runBlocking {
        repository.insertReminder(medicine())
        repository.setCompleted("med", true, scheduler.localClock())
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))
    }

    @Test
    fun `Alarms do not notify for disabled, completed or long-missed reminders`() = runBlocking {
        repository.insertReminder(medicine())

        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 10, 5) // more than an hour late, e.g. the phone was off
        handler.onAlarm("med", AlarmKind.DAILY)
        assertEquals(0, display.showCount)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))

        nowMillis = at(2026, Calendar.SEPTEMBER, 14, 8, 20) // a little late still counts
        handler.onAlarm("med", AlarmKind.DAILY)
        assertEquals(1, display.showCount)

        repository.setEnabled("med", false)
        handler.onAlarm("med", AlarmKind.DAILY)
        assertEquals(1, display.showCount)
        assertTrue(gateway.alarms.isEmpty())
    }

    @Test
    fun `Daily rollover schedules the next day, across midnight and month end`() = runBlocking {
        repository.insertReminder(medicine())
        nowMillis = at(2026, Calendar.SEPTEMBER, 13, 8, 0)
        handler.onAlarm("med", AlarmKind.DAILY)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0), gateway.daily("med"))

        nowMillis = at(2026, Calendar.SEPTEMBER, 30, 23, 59)
        repository.insertReminder(Reminder(id = "late", title = "Night check", minuteOfDay = 5))
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0, 5), gateway.daily("late"))

        // Completed yesterday is not done today: the next alarm notifies again.
        repository.setCompleted("late", true, scheduler.localClock())
        nowMillis = at(2026, Calendar.OCTOBER, 1, 0, 5)
        handler.onAlarm("late", AlarmKind.DAILY)
        assertEquals("shown", display.visible["late"])
    }

    @Test
    fun `Times follow the device time zone`() {
        val instant = at(2026, Calendar.SEPTEMBER, 13, 7, 0, TimeZone.getTimeZone("UTC"))
        val reminder = medicine()

        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        val newYork = TimeZone.getTimeZone("America/New_York")
        // 07:00 UTC is 12:30 in India (8:00 has passed) and 03:00 in New York (8:00 is ahead).
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 8, 0, kolkata), ReminderScheduler.nextDailyTrigger(reminder, instant, kolkata))
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 0, newYork), ReminderScheduler.nextDailyTrigger(reminder, instant, newYork))

        // Moving the phone to another time zone and rescheduling uses the new local 8:00.
        repositoryInsertAndReschedule(reminder, newYork, instant)
        assertEquals(at(2026, Calendar.SEPTEMBER, 13, 8, 0, newYork), gateway.daily("med"))
    }

    private fun repositoryInsertAndReschedule(reminder: Reminder, tz: TimeZone, instant: Long) = runBlocking {
        zone = TimeZone.getTimeZone("Asia/Kolkata")
        nowMillis = instant
        repository.insertReminder(reminder)
        zone = tz
        handler.rescheduleAll()
    }

    @Test
    fun `Daylight saving days keep the local reminder time`() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val reminder = medicine()

        // Clocks go forward on 8 March 2026: that day is 23 hours long.
        val beforeSpring = ReminderScheduler.nextDailyTrigger(reminder, at(2026, Calendar.MARCH, 7, 9, 0, newYork), newYork)
        assertEquals(at(2026, Calendar.MARCH, 8, 8, 0, newYork), beforeSpring)
        assertEquals(23 * 3_600_000L, beforeSpring - at(2026, Calendar.MARCH, 7, 8, 0, newYork))

        // Clocks go back on 1 November 2026: that day is 25 hours long.
        val beforeAutumn = ReminderScheduler.nextDailyTrigger(reminder, at(2026, Calendar.OCTOBER, 31, 9, 0, newYork), newYork)
        assertEquals(25 * 3_600_000L, beforeAutumn - at(2026, Calendar.OCTOBER, 31, 8, 0, newYork))
        assertEquals(8, Calendar.getInstance(newYork).apply { timeInMillis = beforeAutumn }.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `Notification permission is requested at most once`() {
        assertEquals(NotificationAccess.GRANTED, NotificationPermission.resolve(32, permissionGranted = false, notificationsEnabled = true, askedBefore = false))
        assertEquals(NotificationAccess.OPEN_SETTINGS, NotificationPermission.resolve(32, permissionGranted = false, notificationsEnabled = false, askedBefore = false))
        assertEquals(NotificationAccess.CAN_ASK, NotificationPermission.resolve(33, permissionGranted = false, notificationsEnabled = false, askedBefore = false))
        assertEquals(NotificationAccess.OPEN_SETTINGS, NotificationPermission.resolve(33, permissionGranted = false, notificationsEnabled = false, askedBefore = true))
        assertEquals(NotificationAccess.GRANTED, NotificationPermission.resolve(36, permissionGranted = true, notificationsEnabled = true, askedBefore = true))
        // Permission granted but notifications blocked in settings: never prompt, offer settings.
        assertEquals(NotificationAccess.OPEN_SETTINGS, NotificationPermission.resolve(36, permissionGranted = true, notificationsEnabled = false, askedBefore = false))
    }
}
