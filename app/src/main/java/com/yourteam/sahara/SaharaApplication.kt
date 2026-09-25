package com.yourteam.sahara

import android.app.Application
import android.content.Context
import com.yourteam.sahara.api.RetrofitProvider
import com.yourteam.sahara.auth.SecureTokenStore
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.remote.BackendAuthService
import com.yourteam.sahara.data.remote.RetrofitRemoteDataSource
import com.yourteam.sahara.data.repository.CaregiverAlertRepository
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.language.LanguageManager
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.notifications.AndroidAlarmGateway
import com.yourteam.sahara.notifications.ReminderNotificationHandler
import com.yourteam.sahara.notifications.ReminderNotificationSync
import com.yourteam.sahara.notifications.ReminderNotifier
import com.yourteam.sahara.notifications.ReminderScheduler
import com.yourteam.sahara.sync.NetworkMonitor
import com.yourteam.sahara.sync.PatientPullSyncService
import com.yourteam.sahara.sync.ReminderPullSyncService
import com.yourteam.sahara.sync.SyncManager
import com.yourteam.sahara.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SaharaApplication : Application() {

    // Process-lifetime scope for work that must outlive any single screen -- e.g. the Stage 3
    // background backend verification below, which must not be cancelled just because the login
    // screen that started it has already navigated away (a real bug: `rememberCoroutineScope()`
    // in AccountScreens.kt was cancelled the instant login succeeded and the screen was replaced,
    // so the backend call never got the chance to complete). SupervisorJob so one failed call
    // (backend unreachable, timeout, etc.) never cancels this scope for the rest of the app's life.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }
    val authDataSource by lazy { com.yourteam.sahara.auth.RoomAuthDataSource(database) }

    // Stage 3B: the sync queue now uploads to the real FastAPI backend instead of the
    // in-memory SimulatedRemoteDataSource (which remains for tests/offline-demo use).
    val syncManager by lazy { SyncManager(database, networkMonitor, authDataSource, RetrofitRemoteDataSource(backendApiService)) }
    val patientPullSyncService by lazy { PatientPullSyncService(RetrofitRemoteDataSource(backendApiService), database) }
    val reminderPullSyncService by lazy { ReminderPullSyncService(RetrofitRemoteDataSource(backendApiService), database) }

    val authRepository by lazy { com.yourteam.sahara.auth.AuthRepository(authDataSource) }
    val accountsReady = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun gameResultsFor(patientId: String) = GameResultRepository(database.gameResultDao(), syncManager, patientId, authRepository)
    fun remindersFor(patientId: String) = ReminderRepository(database.reminderDao(),
        listener = ReminderNotificationSync(reminderScheduler, reminderNotifier), scopePatientId = patientId, auth = authRepository, syncManager = syncManager)
    val protectedPatientRepository by lazy { PatientRepository(database.patientDao(), syncManager, authRepository) }

    // Internal storage access is reserved for migration, scheduling and instrumentation.
    internal val gameResultRepository by lazy { GameResultRepository(database.gameResultDao(), syncManager) }
    internal val patientRepository by lazy { PatientRepository(database.patientDao(), syncManager) }
    val reminderNotifier by lazy { ReminderNotifier(this) { languageManager.currentLanguage.value.code } }
    val reminderScheduler by lazy { ReminderScheduler(AndroidAlarmGateway(this)) }
    internal val reminderRepository by lazy {
        ReminderRepository(database.reminderDao(), listener = ReminderNotificationSync(reminderScheduler, reminderNotifier), syncManager = syncManager)
    }
    val reminderNotificationHandler by lazy { ReminderNotificationHandler(reminderRepository, reminderScheduler, reminderNotifier) }
    val caregiverAlertRepository by lazy { CaregiverAlertRepository(database.caregiverAlertDao()) }

    val languageManager by lazy { LanguageManager(this) }
    val voiceManager by lazy { VoiceManager(this) }

    // Stage 3 vertical slice: a background, best-effort verification that the same
    // credentials a caregiver signs in with locally also work against the real FastAPI
    // backend. See BackendAuthService's doc comment -- this never blocks or affects the
    // existing local (Room-backed) login flow above.
    val backendTokenStore by lazy { SecureTokenStore(this) }
    val backendApiService by lazy { RetrofitProvider.create(BuildConfig.API_BASE_URL) { backendTokenStore.getToken() } }
    val backendAuthService by lazy { BackendAuthService(backendApiService, backendTokenStore) }

    override fun onCreate() {
        super.onCreate()

        // Apply the persisted UI language before any Activity is created.
        languageManager.applySavedLanguageIfNeeded()

        // Seed demo data exactly once per install. Previously this ran on every
        // launch with OnConflictStrategy.REPLACE, which silently reset any
        // reminder the user had marked complete.
        reminderNotifier.ensureChannel()
        val prefs = getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)
        CoroutineScope(Dispatchers.IO).launch {
            val demo = com.yourteam.sahara.auth.DemoIdentity
            val existing = database.patientDao().getPatientByIdSync(demo.PATIENT_ID)
            // The demo caregiver's password is published in source, so it exists only in debug builds.
            // Release builds never create it; caregivers register their own local account instead.
            if (BuildConfig.DEBUG) {
                if (database.authDao().account(demo.LOGIN) == null) {
                    val verifier = com.yourteam.sahara.auth.PasswordHasher().hash(demo.PASSWORD)
                    database.authDao().insertAccount(com.yourteam.sahara.auth.AccountEntity(
                        demo.CAREGIVER_ID, demo.LOGIN, verifier.hash, verifier.salt, verifier.algorithm, verifier.iterations, 0))
                }
                if (existing == null) {
                    // Briefly assume the demo caregiver's session so the resulting sync-queue item
                    // is attributed to the demo caregiver (SyncManager derives ownership from the
                    // live session, never from caller input) -- then restore whatever session was
                    // active before seeding so this never auto-logs a real caregiver out, and never
                    // silently signs a fresh install in as the demo caregiver.
                    val sessionBeforeSeeding = database.authDao().session()
                    database.authDao().saveSession(com.yourteam.sahara.auth.LocalSessionEntity(caregiverId = demo.CAREGIVER_ID))
                    patientRepository.insertPatient(Patient(id = demo.PATIENT_ID, syncId = demo.PATIENT_ID,
                        name = "Kamala Devi", age = 74, region = "Assam", language = "Assamese"))
                    if (sessionBeforeSeeding != null) {
                        database.authDao().saveSession(sessionBeforeSeeding)
                    } else {
                        database.authDao().clearSession()
                    }
                }
                database.authDao().link(com.yourteam.sahara.auth.CaregiverPatientEntity(demo.CAREGIVER_ID, demo.PATIENT_ID, 0))
                if (!prefs.getBoolean(KEY_SEEDED, false)) {
                    reminderRepository.seedBuiltInReminders(demo.PATIENT_ID)
                    prefs.edit().putBoolean(KEY_SEEDED, true).apply()
                }
                if (backendTokenStore.getToken() == null) {
                    val outcome = backendAuthService.loginAndVerify(demo.LOGIN, demo.PASSWORD)
                    if (outcome is BackendAuthService.Outcome.Failed) {
                        backendAuthService.registerAndVerify(demo.LOGIN, demo.PASSWORD)
                    }
                }
            }
            accountsReady.value = true
            // Alarms can be lost (force stop, reinstall); rescheduling replaces rather than duplicates them.
            reminderNotificationHandler.rescheduleAll()
        }
    }

    // NOTE: Application.onTerminate() is never called on a real device -- it only
    // runs in emulated process environments. The previous voiceManager.destroy()
    // call here was dead code. VoiceManager is process-scoped and is released when
    // the process dies; see the audit notes before adding Activity-scoped teardown.

    private companion object {
        const val SEED_PREFS = "sahara_app_prefs"
        const val KEY_SEEDED = "initial_data_seeded_v1"
    }
}
