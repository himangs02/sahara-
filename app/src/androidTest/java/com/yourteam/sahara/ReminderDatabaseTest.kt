package com.yourteam.sahara

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.auth.DemoIdentity
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.BuiltInReminders
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ReminderDatabaseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "reminder-test.db"

    @Before @After
    fun clean() {
        context.deleteDatabase(dbName)
    }

    private fun openRoom() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8
        )
        .build()

    /** Writes a database exactly as version 4 of the app left it. */
    private fun createVersion4(populate: (SupportSQLiteDatabase) -> Unit) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT NOT NULL, `gameType` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `totalPairs` INTEGER NOT NULL, `matchedPairs` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, `completionTimeSeconds` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `completed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `patients` (`id` TEXT NOT NULL, `syncId` TEXT NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `region` TEXT NOT NULL, `language` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `lastError` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `scheduledTime` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `caregiver_alerts` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `activityType` TEXT, `severity` TEXT NOT NULL, `reviewed` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    populate(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply { writableDatabase; close() }
    }

    /**
     * Upgrades a version-4 database through 5 (daily reminders) and 6 (accounts). The legacy demo
     * patient "patient_001" and everything that referenced it move to the stable demo UUID, while
     * data belonging to any other patient id is left exactly as it was.
     */
    @Test
    fun migration4To6PreservesDataAndMovesItIntoTheAccountModel() {
        val demo = DemoIdentity.PATIENT_ID
        createVersion4 { db ->
            db.execSQL("INSERT INTO reminders VALUES ('rem_001', 'patient_001', 'Morning Medicine', 'MEDICINE', '8:00 AM', 0, 'COMPLETED', 1)")
            db.execSQL("INSERT INTO reminders VALUES ('user_1', 'patient_001', 'शाम की सैर', 'GENERAL', '6:30 PM', 0, 'UPCOMING', 0)")
            db.execSQL("INSERT INTO reminders VALUES ('other_1', 'other_patient', 'Other tea', 'GENERAL', '4:00 PM', 0, 'UPCOMING', 1)")
            db.execSQL("INSERT INTO patients VALUES ('patient_001', 'patient_001', 'Kamala Devi', 74, 'Assam', 'Assamese')")
            db.execSQL("INSERT INTO game_results (syncId, gameType, difficulty, totalPairs, matchedPairs, mistakes, completionTimeSeconds, accuracy, completed, timestamp) VALUES ('legacy-1', 'MEMORY_MATCH', 'EASY', 4, 4, 1, 30, 90.0, 1, 1000)")
            db.execSQL("INSERT INTO caregiver_alerts VALUES ('alert_inactivity', 'patient_001', 'INACTIVITY', '', NULL, 'INFO', 0, 1000)")
        }

        val db = openRoom()
        try {
            // Opening through Room also validates the migrated schema, including the new account tables.
            assertTrue("Legacy patient id must not survive", db.reminderDao().getRemindersForPatientSync("patient_001").isEmpty())

            val rows = db.reminderDao().getRemindersForPatientSync(demo).map { it.toDomain() }
            assertEquals(listOf("rem_001", "user_1"), rows.map { it.id })
            assertEquals(listOf("other_1"), db.reminderDao().getRemindersForPatientSync("other_patient").map { it.id })

            val legacyResult = db.gameResultDao().getAllGameResultsSync().single()
            assertEquals("Existing game history belongs to the demo patient", demo, legacyResult.patientId)
            assertEquals(90f, legacyResult.accuracy, 0.01f)

            val migratedPatient = db.patientDao().getPatientByIdSync(demo)
            assertEquals("Kamala Devi", migratedPatient?.name)
            assertEquals(demo, migratedPatient?.syncId)
            assertEquals(null, db.patientDao().getPatientByIdSync("patient_001"))

            db.openHelper.readableDatabase.query("SELECT patientId FROM caregiver_alerts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(demo, cursor.getString(0))
            }
            // Migration creates the account tables but never invents accounts or sessions.
            db.openHelper.readableDatabase.query("SELECT (SELECT COUNT(*) FROM accounts) + (SELECT COUNT(*) FROM local_session)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            val medicine = rows[0]
            assertEquals("Morning Medicine", medicine.title)
            assertEquals(8 * 60, medicine.minuteOfDay)
            assertTrue(medicine.enabled)
            assertEquals(Reminder.NOT_COMPLETED, medicine.lastCompletedEpochDay)
            assertEquals("", medicine.description)
            assertTrue(medicine.createdAt > 0)

            val walk = rows[1]
            assertEquals("शाम की सैर", walk.title)
            assertEquals(18 * 60 + 30, walk.minuteOfDay)
            assertFalse(walk.enabled)
        } finally {
            db.close()
        }
    }

    /**
     * Migration 6→7 adds caregiver ownership to the sync queue (Stage 3B hardening). Existing
     * pending rows written before the migration have no recorded owner, and the migration must
     * leave them that way (NULL) rather than guessing an owner -- SyncManager treats NULL as
     * orphaned and never uploads it under whichever caregiver happens to be logged in later.
     */
    @Test
    fun migration6To7AddsCaregiverColumnWithoutGuessingOwnership() {
        // Build a real version-6 database by replaying the app's own migrations (rather than
        // hand-writing the schema, which is easy to get subtly wrong vs. what Room expects),
        // then insert a pending queue row exactly as it would have existed before ownership
        // was introduced -- the "stale demo/debug queue item" scenario reported in the field.
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT NOT NULL, `gameType` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `totalPairs` INTEGER NOT NULL, `matchedPairs` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, `completionTimeSeconds` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `completed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `patients` (`id` TEXT NOT NULL, `syncId` TEXT NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `region` TEXT NOT NULL, `language` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `lastError` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `scheduledTime` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `caregiver_alerts` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `activityType` TEXT, `severity` TEXT NOT NULL, `reviewed` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    // Version 4 -> 5 -> 6, replaying the app's real migrations so the resulting
                    // schema is byte-for-byte what Room itself would have produced.
                    AppDatabase.MIGRATION_4_5.migrate(db)
                    AppDatabase.MIGRATION_5_6.migrate(db)
                    // A pending queue row written before caregiver ownership existed.
                    db.execSQL(
                        "INSERT INTO sync_queue (entityType, entityId, operation, syncStatus, retryCount, createdAt, lastAttemptAt, lastError) " +
                            "VALUES ('PATIENT', 'pre_existing_patient', 'INSERT', 'PENDING', 0, 1000, 0, NULL)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply { writableDatabase; close() }

        val db = openRoom()
        try {
            // Opening through Room validates the migrated schema (new caregiverId column present).
            val row = db.openHelper.readableDatabase.query("SELECT entityId, caregiverId FROM sync_queue WHERE entityId = 'pre_existing_patient'")
            row.use { cursor ->
                assertTrue("Pre-existing queue row must survive the migration", cursor.moveToFirst())
                assertTrue("Pre-migration row must have no owner assigned (never guess ownership)", cursor.isNull(cursor.getColumnIndexOrThrow("caregiverId")))
            }
        } finally {
            db.close()
        }
    }

    /**
     * Migration 7→8 (Stage 3C) adds patientId to the sync queue, needed once reminders join it
     * (reminder API calls are patient-scoped, and a DELETE's local Room row is already gone by
     * the time the queue is processed). Pre-existing rows must survive with patientId = NULL.
     */
    @Test
    fun migration7To8AddsPatientIdColumnAndPreservesExistingRows() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT NOT NULL, `gameType` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `totalPairs` INTEGER NOT NULL, `matchedPairs` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, `completionTimeSeconds` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `completed` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `patients` (`id` TEXT NOT NULL, `syncId` TEXT NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `region` TEXT NOT NULL, `language` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `lastError` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `scheduledTime` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `caregiver_alerts` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `activityType` TEXT, `severity` TEXT NOT NULL, `reviewed` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    AppDatabase.MIGRATION_4_5.migrate(db)
                    AppDatabase.MIGRATION_5_6.migrate(db)
                    AppDatabase.MIGRATION_6_7.migrate(db)
                    // A pending queue row written before reminders joined the sync queue.
                    db.execSQL(
                        "INSERT INTO sync_queue (entityType, entityId, operation, syncStatus, retryCount, createdAt, lastAttemptAt, lastError, caregiverId) " +
                            "VALUES ('PATIENT', 'pre_existing_patient', 'INSERT', 'PENDING', 0, 1000, 0, NULL, 'caregiver_x')"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply { writableDatabase; close() }

        val db = openRoom()
        try {
            val row = db.openHelper.readableDatabase.query(
                "SELECT entityId, caregiverId, patientId FROM sync_queue WHERE entityId = 'pre_existing_patient'"
            )
            row.use { cursor ->
                assertTrue("Pre-existing queue row must survive the migration", cursor.moveToFirst())
                assertEquals("caregiver_x", cursor.getString(cursor.getColumnIndexOrThrow("caregiverId")))
                assertTrue("Pre-migration row must have no patientId (only reminders ever set it)", cursor.isNull(cursor.getColumnIndexOrThrow("patientId")))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun completionSurvivesRestartAndResetsNextDay() = runBlocking {
        val today = LocalClock.now()
        var db = openRoom()
        ReminderRepository(db.reminderDao()).apply {
            seedBuiltInReminders()
            setCompleted("rem_001", true, today)
        }
        db.close()

        // A new Room instance reads the same file, as after a process restart.
        db = openRoom()
        try {
            val repository = ReminderRepository(db.reminderDao())
            repository.seedBuiltInReminders()
            val reminders = repository.getRemindersForPatient().first()
            assertEquals(BuiltInReminders.all().size, reminders.size)

            val medicine = reminders.first { it.id == "rem_001" }
            assertEquals(ReminderStatus.COMPLETED, medicine.statusAt(today))
            assertNotEquals(ReminderStatus.COMPLETED, medicine.statusAt(LocalClock(today.epochDay + 1, 0)))
        } finally {
            db.close()
        }
    }

    private val reminderStringIds = listOf(
        R.string.todays_care, R.string.no_reminders_today, R.string.daily_reminders, R.string.add_reminder,
        R.string.edit_reminder, R.string.reminder_title_label, R.string.reminder_description_label,
        R.string.reminder_type_label, R.string.reminder_time_label, R.string.type_medicine, R.string.type_hydration,
        R.string.type_activity, R.string.type_appointment, R.string.type_general, R.string.save, R.string.delete,
        R.string.delete_reminder_confirm, R.string.reminder_upcoming, R.string.reminder_completed,
        R.string.reminder_missed, R.string.reminder_off, R.string.mark_done, R.string.toggle_reminder_status,
        R.string.toggle_reminder_enabled, R.string.morning_medicine, R.string.hydration,
        R.string.reminder_cognitive_activity, R.string.reminder_doctor_appointment, R.string.manage_reminders
    )

    private fun stringsIn(language: String): List<String> {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = context.createConfigurationContext(config)
        return reminderStringIds.map { localized.getString(it) }
    }

    private fun assertTranslated(language: String, script: Regex) {
        val english = stringsIn("en")
        stringsIn(language).forEachIndexed { index, text ->
            val name = context.resources.getResourceEntryName(reminderStringIds[index])
            assertTrue("$language/$name is blank", text.isNotBlank())
            assertNotEquals("$language/$name is untranslated", english[index], text)
            assertTrue("$language/$name is not in the expected script: $text", script.containsMatchIn(text))
        }
    }

    @Test
    fun reminderStringsAreTranslatedToHindi() = assertTranslated("hi", Regex("[\\u0900-\\u097F]"))

    @Test
    fun reminderStringsAreTranslatedToAssamese() = assertTranslated("as", Regex("[\\u0980-\\u09FF]"))
}
