package com.yourteam.sahara

import android.app.Application
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.repository.CaregiverAlertRepository
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.language.LanguageManager
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.ReminderType
import com.yourteam.sahara.sync.NetworkMonitor
import com.yourteam.sahara.sync.SyncManager
import com.yourteam.sahara.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaharaApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }
    val syncManager by lazy { SyncManager(database, networkMonitor) }

    val gameResultRepository by lazy { GameResultRepository(database.gameResultDao(), syncManager) }
    val patientRepository by lazy { PatientRepository(database.patientDao(), syncManager) }
    val reminderRepository by lazy { ReminderRepository(database.reminderDao()) }
    val caregiverAlertRepository by lazy { CaregiverAlertRepository(database.caregiverAlertDao()) }

    val languageManager by lazy { LanguageManager(this) }
    val voiceManager by lazy { VoiceManager(this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            patientRepository.insertPatient(Patient())
            seedSampleReminders()
        }
    }

    private suspend fun seedSampleReminders() {
        val sampleReminders = listOf(
            Reminder(
                id = "rem_001",
                title = "Morning Medicine",
                type = ReminderType.MEDICINE,
                scheduledTime = "8:00 AM",
                timeMillis = System.currentTimeMillis() - 3600000 * 2,
                status = ReminderStatus.COMPLETED
            ),
            Reminder(
                id = "rem_002",
                title = "Hydration",
                type = ReminderType.HYDRATION,
                scheduledTime = "11:00 AM",
                timeMillis = System.currentTimeMillis() + 3600000 * 2,
                status = ReminderStatus.UPCOMING
            ),
            Reminder(
                id = "rem_003",
                title = "Cognitive Activity",
                type = ReminderType.COGNITIVE_ACTIVITY,
                scheduledTime = "4:00 PM",
                timeMillis = System.currentTimeMillis() + 3600000 * 7,
                status = ReminderStatus.UPCOMING
            ),
            Reminder(
                id = "rem_004",
                title = "Doctor Appointment",
                type = ReminderType.APPOINTMENT,
                scheduledTime = "6:00 PM",
                timeMillis = System.currentTimeMillis() + 3600000 * 9,
                status = ReminderStatus.UPCOMING
            )
        )
        sampleReminders.forEach { reminderRepository.insertReminder(it) }
    }

    override fun onTerminate() {
        super.onTerminate()
        voiceManager.destroy()
    }
}
