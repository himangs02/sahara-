package com.yourteam.sahara.data.remote

import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder

interface RemoteDataSource {
    suspend fun uploadGameResult(result: GameResult): Boolean
    suspend fun uploadPatient(patient: Patient): Boolean
    suspend fun fetchGameResults(): List<GameResult>

    /** All patients the authenticated caregiver is linked to, per the backend. Used to
     * populate Room right after login (Stage 3B) -- see PatientPullSyncService. */
    suspend fun fetchPatients(): List<Patient>

    /** Game results for one authorized patient, newest first. */
    suspend fun fetchGameResultsForPatient(patientId: String): List<GameResult>

    /** Creates or updates [reminder]'s *definition* on the backend (idempotent upsert by
     * id) for [patientId]. Only ever called for a reminder already known to belong to
     * [patientId] (see SyncManager) -- device-specific state never uploads (Stage 3C). */
    suspend fun uploadReminder(patientId: String, reminder: Reminder): Boolean

    /** Deletes reminder [reminderId] for [patientId]. Returns true if the deletion
     * succeeded OR the reminder was already gone (idempotent from the queue's view). */
    suspend fun deleteReminder(patientId: String, reminderId: String): Boolean

    /** Reminder definitions for one authorized patient, used to populate Room right
     * after login and reschedule local alarms (Stage 3C) -- see ReminderPullSyncService. */
    suspend fun fetchRemindersForPatient(patientId: String): List<Reminder>
}
