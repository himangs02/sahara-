package com.yourteam.sahara.reminder

import com.yourteam.sahara.data.local.ReminderDao
import com.yourteam.sahara.data.local.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory ReminderDao with Room's conflict behaviour, shared by the reminder unit tests. */
class FakeReminderDao : ReminderDao {
    val rows = MutableStateFlow<Map<String, ReminderEntity>>(emptyMap())

    override fun insertReminder(reminder: ReminderEntity) {
        rows.value = rows.value + (reminder.id to reminder)
    }

    override fun insertRemindersIfAbsent(reminders: List<ReminderEntity>) {
        rows.value = rows.value + reminders.filter { it.id !in rows.value }.associateBy { it.id }
    }

    override fun updateReminder(reminder: ReminderEntity) {
        if (reminder.id in rows.value) rows.value = rows.value + (reminder.id to reminder)
    }

    override fun deleteReminder(reminder: ReminderEntity) {
        rows.value = rows.value - reminder.id
    }

    override fun getReminderById(id: String) = rows.value[id]

    override fun getRemindersForPatient(patientId: String): Flow<List<ReminderEntity>> =
        rows.map { all -> all.values.filter { it.patientId == patientId }.sortedBy { it.minuteOfDay } }

    override fun getRemindersForPatientSync(patientId: String) =
        rows.value.values.filter { it.patientId == patientId }.sortedBy { it.minuteOfDay }

    override fun getAllRemindersSync() = rows.value.values.toList()
}
