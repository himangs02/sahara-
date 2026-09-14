package com.yourteam.sahara.data.repository

import com.yourteam.sahara.auth.PatientAccessDeniedException
import com.yourteam.sahara.data.local.ReminderDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.BuiltInReminders
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.sync.SyncManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Told about every stored reminder change, so notifications always follow the database. */
interface ReminderChangeListener {
    fun onReminderSaved(reminder: Reminder)
    fun onReminderDeleted(reminder: Reminder)
}

/**
 * Reminder storage, offline-first: every write lands in Room immediately (so local alarm
 * scheduling never depends on network) and, when [syncManager] is supplied, is also queued for
 * upload (Stage 3C). Only the reminder *definition* (title/description/type/time/enabled) is
 * queued -- device-specific state like today's completion ([setCompleted]) never syncs.
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val listener: ReminderChangeListener? = null,
    private val scopePatientId: String? = null,
    private val auth: com.yourteam.sahara.auth.AuthRepository? = null,
    private val syncManager: SyncManager? = null
) {

    private suspend fun checkAccess(patientId: String) {
        if (scopePatientId != null && patientId != scopePatientId) throw PatientAccessDeniedException("reminder belongs to another patient")
        auth?.requirePatient(patientId)
    }
    private suspend fun checkRow(reminder: Reminder) {
        checkAccess(reminder.patientId)
        dao.getReminderById(reminder.id)?.let { checkAccess(it.patientId) }
    }

    /** Adds a new reminder or replaces an edited one with the same id. */
    suspend fun insertReminder(reminder: Reminder) = withContext(io) {
        checkRow(reminder)
        dao.insertReminder(reminder.toEntity())
        listener?.onReminderSaved(reminder)
        syncManager?.enqueueReminderSync(reminder.id, reminder.patientId, "INSERT")
    }

    suspend fun updateReminder(reminder: Reminder) = withContext(io) {
        checkRow(reminder)
        dao.updateReminder(reminder.toEntity())
        listener?.onReminderSaved(reminder)
        syncManager?.enqueueReminderSync(reminder.id, reminder.patientId, "UPDATE")
    }

    suspend fun deleteReminder(reminder: Reminder) = withContext(io) {
        checkRow(reminder)
        dao.deleteReminder(reminder.toEntity())
        listener?.onReminderDeleted(reminder)
        syncManager?.enqueueReminderSync(reminder.id, reminder.patientId, "DELETE")
    }

    suspend fun getReminder(id: String): Reminder? = withContext(io) {
        dao.getReminderById(id)?.also { checkAccess(it.patientId) }?.toDomain()
    }

    suspend fun getAllReminders(): List<Reminder> = withContext(io) {
        dao.getAllRemindersSync().filter { scopePatientId == null || it.patientId == scopePatientId }.onEach { checkAccess(it.patientId) }.map { it.toDomain() }
    }

    /**
     * Re-reads the stored row so a stale UI copy cannot overwrite a newer edit. Completion is a date,
     * so marking done twice on the same day stores the same value and never duplicates anything.
     */
    suspend fun setCompleted(id: String, done: Boolean, clock: LocalClock = LocalClock.now()) = withContext(io) {
        dao.getReminderById(id)?.let {
            checkAccess(it.patientId)
            val updated = it.toDomain().withCompleted(done, clock)
            dao.updateReminder(updated.toEntity())
            listener?.onReminderSaved(updated)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(io) {
        dao.getReminderById(id)?.let {
            checkAccess(it.patientId)
            val updated = it.copy(enabled = enabled)
            dao.updateReminder(updated)
            listener?.onReminderSaved(updated.toDomain())
            // enabled is part of the synced definition (unlike completion, which is device-only).
            syncManager?.enqueueReminderSync(updated.id, updated.patientId, "UPDATE")
        }
    }

    /** Safe to call repeatedly: existing reminders (including edited or completed built-ins) are never replaced. */
    suspend fun seedBuiltInReminders(patientId: String = scopePatientId ?: com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID) = withContext(io) {
        checkAccess(patientId)
        dao.insertRemindersIfAbsent(BuiltInReminders.all().map { it.copy(patientId = patientId).toEntity() })
    }

    fun getRemindersForPatient(patientId: String = scopePatientId ?: com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID): Flow<List<Reminder>> {
        if (scopePatientId != null && scopePatientId != patientId) throw PatientAccessDeniedException("reminders of another patient")
        val authRepo = auth
        if (authRepo != null) return kotlinx.coroutines.flow.combine(dao.getRemindersForPatient(patientId), authRepo.sessions) { rows, _ ->
            if (runCatching { authRepo.requirePatient(patientId) }.isSuccess) rows.map { it.toDomain() } else emptyList()
        }
        return dao.getRemindersForPatient(patientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
