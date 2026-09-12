package com.yourteam.sahara.data.repository

import com.yourteam.sahara.data.local.ReminderDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReminderRepository(private val dao: ReminderDao) {

    suspend fun insertReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        dao.insertReminder(reminder.toEntity())
    }

    suspend fun updateReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        dao.updateReminder(reminder.toEntity())
    }

    suspend fun deleteReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        dao.deleteReminder(reminder.toEntity())
    }

    fun getRemindersForPatient(patientId: String = "patient_001"): Flow<List<Reminder>> {
        return dao.getRemindersForPatient(patientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
