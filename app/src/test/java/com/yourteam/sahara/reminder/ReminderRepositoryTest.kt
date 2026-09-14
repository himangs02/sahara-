package com.yourteam.sahara.reminder

import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.ReminderType
import com.yourteam.sahara.model.forToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRepositoryTest {

    private val dao = FakeReminderDao()
    private val repository = ReminderRepository(dao, Dispatchers.Unconfined)
    private val today = LocalClock(epochDay = 20_709, minuteOfDay = 7 * 60)

    private fun stored(): List<Reminder> = runBlocking { repository.getRemindersForPatient().first() }
    private fun stored(id: String) = stored().find { it.id == id }

    @Test
    fun `Creating a reminder stores every field`() = runBlocking {
        repository.insertReminder(
            Reminder(id = "tea", title = "Evening tea", description = "With biscuits", type = ReminderType.GENERAL,
                minuteOfDay = 17 * 60 + 30, createdAt = 1234L)
        )
        val tea = stored("tea")!!
        assertEquals("Evening tea", tea.title)
        assertEquals("With biscuits", tea.description)
        assertEquals(17 * 60 + 30, tea.minuteOfDay)
        assertEquals(1234L, tea.createdAt)
        assertTrue(tea.enabled)
        assertEquals(Reminder.NOT_COMPLETED, tea.lastCompletedEpochDay)
    }

    @Test
    fun `Editing a reminder keeps its id and creation time`() = runBlocking {
        repository.insertReminder(Reminder(id = "walk", title = "Walk", minuteOfDay = 17 * 60, createdAt = 99L))
        repository.insertReminder(stored("walk")!!.copy(title = "Short walk", minuteOfDay = 18 * 60, type = ReminderType.GENERAL))

        assertEquals(1, stored().size)
        val walk = stored("walk")!!
        assertEquals("Short walk", walk.title)
        assertEquals(18 * 60, walk.minuteOfDay)
        assertEquals(99L, walk.createdAt)
    }

    @Test
    fun `Deleting a reminder removes only that reminder`() = runBlocking {
        repository.insertReminder(Reminder(id = "a", title = "A", minuteOfDay = 60))
        repository.insertReminder(Reminder(id = "b", title = "B", minuteOfDay = 120))
        repository.deleteReminder(stored("a")!!)

        assertNull(stored("a"))
        assertEquals(listOf("b"), stored().map { it.id })
    }

    @Test
    fun `Disabled reminders are hidden from the elderly list but kept for the caregiver`() = runBlocking {
        repository.insertReminder(Reminder(id = "a", title = "A", minuteOfDay = 60))
        repository.setEnabled("a", false)

        assertFalse(stored("a")!!.enabled)
        assertTrue(stored().forToday(today, includeDisabled = false).isEmpty())
        assertEquals(1, stored().forToday(today, includeDisabled = true).size)

        repository.setEnabled("a", true)
        assertEquals(1, stored().forToday(today, includeDisabled = false).size)
    }

    @Test
    fun `Marking done persists for today and reads as incomplete the next day`() = runBlocking {
        repository.insertReminder(Reminder(id = "med", title = "Medicine", minuteOfDay = 8 * 60))
        repository.setCompleted("med", true, today)

        val med = stored("med")!!
        assertEquals(today.epochDay, med.lastCompletedEpochDay)
        assertEquals(ReminderStatus.COMPLETED, med.statusAt(today))
        assertEquals(ReminderStatus.UPCOMING, med.statusAt(LocalClock(today.epochDay + 1, 7 * 60)))

        repository.setCompleted("med", false, today)
        assertEquals(ReminderStatus.UPCOMING, stored("med")!!.statusAt(today))
    }

    @Test
    fun `Built-in reminders are seeded once and never duplicated or reset`() = runBlocking {
        repository.insertReminder(Reminder(id = "mine", title = "Hydration", minuteOfDay = 9 * 60))
        repository.seedBuiltInReminders()
        repository.setCompleted("rem_001", true, today)
        repository.insertReminder(stored("rem_002")!!.copy(title = "Water with lemon"))

        repository.seedBuiltInReminders()
        repository.seedBuiltInReminders()

        assertEquals(5, stored().size)
        assertEquals(4, stored().count { it.id.startsWith("rem_") })
        assertEquals(today.epochDay, stored("rem_001")!!.lastCompletedEpochDay)
        assertEquals("Water with lemon", stored("rem_002")!!.title)
        assertEquals("Hydration", stored("mine")!!.title)
    }
}
