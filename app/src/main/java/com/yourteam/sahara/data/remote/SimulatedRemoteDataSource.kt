package com.yourteam.sahara.data.remote

import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SimulatedRemoteDataSource : RemoteDataSource {

    private val remoteResults = ConcurrentHashMap<String, GameResult>()
    private val remotePatients = ConcurrentHashMap<String, Patient>()
    private val remoteReminders = ConcurrentHashMap<String, Reminder>()

    var shouldSimulateError = false

    override suspend fun uploadGameResult(result: GameResult): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(300) // Simulate network latency
        remoteResults[result.syncId] = result // Idempotent upsert by syncId
        true
    }

    override suspend fun uploadPatient(patient: Patient): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(200)
        remotePatients[patient.syncId] = patient
        true
    }

    override suspend fun fetchGameResults(): List<GameResult> = withContext(Dispatchers.IO) {
        delay(300)
        remoteResults.values.toList()
    }

    override suspend fun fetchPatients(): List<Patient> = withContext(Dispatchers.IO) {
        delay(200)
        remotePatients.values.toList()
    }

    override suspend fun fetchGameResultsForPatient(patientId: String): List<GameResult> = withContext(Dispatchers.IO) {
        delay(300)
        remoteResults.values.filter { it.patientId == patientId }
    }

    override suspend fun uploadReminder(patientId: String, reminder: Reminder): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(200)
        remoteReminders[reminder.id] = reminder
        true
    }

    override suspend fun deleteReminder(patientId: String, reminderId: String): Boolean = withContext(Dispatchers.IO) {
        if (shouldSimulateError) return@withContext false
        delay(100)
        remoteReminders.remove(reminderId)
        true
    }

    override suspend fun fetchRemindersForPatient(patientId: String): List<Reminder> = withContext(Dispatchers.IO) {
        delay(200)
        remoteReminders.values.filter { it.patientId == patientId }
    }

    fun getRemoteCount(): Int = remoteResults.size
    fun getRemotePatientCount(): Int = remotePatients.size
    fun getRemoteReminderCount(): Int = remoteReminders.size
}
