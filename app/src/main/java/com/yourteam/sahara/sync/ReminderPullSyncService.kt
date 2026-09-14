package com.yourteam.sahara.sync

import com.yourteam.sahara.api.BackendError
import com.yourteam.sahara.api.toBackendError
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.data.remote.RemoteDataSource
import kotlinx.coroutines.flow.first

/** Stage 3C: after [PatientPullSyncService] has linked this caregiver's authorized patients
 * into local Room, pulls every reminder *definition* for those patients from the backend and
 * upserts them into Room -- so a reminder created on Device A appears on Device B the next time
 * this runs (after login, or a manual sync). Local AlarmManager scheduling is the caller's
 * responsibility (see [com.yourteam.sahara.notifications.ReminderNotificationHandler.rescheduleAll]),
 * exactly like the local-only reminder path already works: this service only fills Room.
 *
 * Never throws -- a caregiver with no network, or an unreachable backend, must keep using
 * whatever reminders are already in local Room exactly as before (same contract as
 * [PatientPullSyncService]).
 */
class ReminderPullSyncService(
    private val remoteDataSource: RemoteDataSource,
    private val database: AppDatabase
) {
    sealed class Outcome {
        data class Success(val reminderCount: Int) : Outcome()
        data class Failed(val error: BackendError) : Outcome()
    }

    suspend fun pullRemindersFor(localCaregiverId: String): Outcome = try {
        val linkedPatients = database.authDao().patients(localCaregiverId).first()
        var total = 0
        linkedPatients.forEach { patient ->
            val reminders = remoteDataSource.fetchRemindersForPatient(patient.id)
            reminders.forEach { reminder ->
                // Preserve this device's own completion state -- the server never knows it,
                // so a pulled definition must not silently un-complete a reminder marked done
                // here today (REMINDER DEFINITION vs. DEVICE-SPECIFIC STATE, Stage 3C Part 9).
                val existing = database.reminderDao().getReminderById(reminder.id)
                val merged = if (existing != null) reminder.copy(lastCompletedEpochDay = existing.lastCompletedEpochDay) else reminder
                database.reminderDao().insertReminder(merged.toEntity())
            }
            total += reminders.size
        }
        Outcome.Success(total)
    } catch (t: Throwable) {
        Outcome.Failed(t.toBackendError())
    }
}
