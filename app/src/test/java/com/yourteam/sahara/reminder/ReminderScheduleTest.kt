package com.yourteam.sahara.reminder

import com.yourteam.sahara.data.local.parseLegacyReminderTime
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.forToday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class ReminderScheduleTest {

    private val medicine = Reminder(id = "m", title = "Medicine", minuteOfDay = 8 * 60)

    @Test
    fun `Completion only counts for the day it was marked`() {
        val monday = LocalClock(epochDay = 100, minuteOfDay = 9 * 60)
        val done = medicine.withCompleted(true, monday)

        assertEquals(ReminderStatus.COMPLETED, done.statusAt(monday))
        assertEquals(ReminderStatus.UPCOMING, done.statusAt(LocalClock(epochDay = 101, minuteOfDay = 7 * 60)))
    }

    @Test
    fun `Reminder becomes missed an hour after its time`() {
        assertEquals(ReminderStatus.UPCOMING, medicine.statusAt(LocalClock(100, 9 * 60)))
        assertEquals(ReminderStatus.MISSED, medicine.statusAt(LocalClock(100, 9 * 60 + 1)))
    }

    @Test
    fun `Undoing completion clears it`() {
        val clock = LocalClock(100, 7 * 60)
        val undone = medicine.withCompleted(true, clock).withCompleted(false, clock)
        assertEquals(ReminderStatus.UPCOMING, undone.statusAt(clock))
    }

    @Test
    fun `Today list skips disabled reminders and sorts by time`() {
        val list = listOf(
            Reminder(id = "late", title = "Walk", minuteOfDay = 17 * 60),
            Reminder(id = "off", title = "Old", minuteOfDay = 6 * 60, enabled = false),
            medicine
        ).forToday(LocalClock(100, 0))

        assertEquals(listOf("m", "late"), list.map { it.reminder.id })
    }

    @Test
    fun `Local clock uses the device time zone`() {
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        // 2026-09-12T20:00Z is 01:30 on 2026-09-13 in India.
        val clock = LocalClock.at(1_789_243_200_000L, kolkata)
        assertEquals(90, clock.minuteOfDay)
        assertEquals(20_709L, clock.epochDay) // 2026-09-13, while UTC is still on day 20_708
        assertEquals(20_708L, LocalClock.at(1_789_243_200_000L, TimeZone.getTimeZone("UTC")).epochDay)
    }

    @Test
    fun `Legacy display times migrate to minutes`() {
        assertEquals(8 * 60, parseLegacyReminderTime("8:00 AM"))
        assertEquals(16 * 60, parseLegacyReminderTime("4:00 PM"))
        assertEquals(0, parseLegacyReminderTime("12:00 AM"))
        assertEquals(12 * 60 + 30, parseLegacyReminderTime("12:30 pm"))
        assertEquals(18 * 60 + 5, parseLegacyReminderTime("18:05"))
        assertEquals(9 * 60, parseLegacyReminderTime("sometime"))
    }
}
