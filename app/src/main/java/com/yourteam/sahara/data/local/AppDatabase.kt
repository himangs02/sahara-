package com.yourteam.sahara.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourteam.sahara.model.Reminder

@Database(
    entities = [
        GameResultEntity::class,
        PatientEntity::class,
        SyncQueueEntity::class,
        ReminderEntity::class,
        CaregiverAlertEntity::class,
        com.yourteam.sahara.auth.AccountEntity::class,
        com.yourteam.sahara.auth.CaregiverPatientEntity::class,
        com.yourteam.sahara.auth.LocalSessionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun authDao(): com.yourteam.sahara.auth.AuthDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun patientDao(): PatientDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun reminderDao(): ReminderDao
    abstract fun caregiverAlertDao(): CaregiverAlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `patients` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `region` TEXT NOT NULL, `language` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `game_results` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `patients` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT 'patient_001'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `syncStatus` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `lastError` TEXT)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `scheduledTime` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `caregiver_alerts` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `activityType` TEXT, `severity` TEXT NOT NULL, `reviewed` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        // Version 5 makes reminders daily: time as minutes after midnight, a description, a
        // created timestamp, and the local day of the last completion (so "done" resets each day).
        // Version 4 stored a display string ("8:00 AM") and a status that never reset; the old
        // COMPLETED status is dropped because it carries no date. All rows are preserved.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders_new` (`id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `type` TEXT NOT NULL, `minuteOfDay` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastCompletedEpochDay` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                val migratedAt = System.currentTimeMillis()
                db.query("SELECT `id`, `patientId`, `title`, `type`, `scheduledTime`, `enabled` FROM `reminders`").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "INSERT INTO `reminders_new` (`id`, `patientId`, `title`, `description`, `type`, `minuteOfDay`, `enabled`, `createdAt`, `lastCompletedEpochDay`) VALUES (?, ?, ?, '', ?, ?, ?, ?, ?)",
                            arrayOf<Any>(
                                cursor.getString(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getString(3),
                                parseLegacyReminderTime(cursor.getString(4)),
                                cursor.getInt(5),
                                migratedAt,
                                Reminder.NOT_COMPLETED
                            )
                        )
                    }
                }
                db.execSQL("DROP TABLE `reminders`")
                db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val demo = com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID
                db.execSQL("CREATE TABLE IF NOT EXISTS accounts (id TEXT NOT NULL PRIMARY KEY, login TEXT NOT NULL, passwordHash TEXT NOT NULL, salt TEXT NOT NULL, algorithm TEXT NOT NULL, iterations INTEGER NOT NULL, consentAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_login ON accounts(login)")
                db.execSQL("CREATE TABLE IF NOT EXISTS caregiver_patients (caregiverId TEXT NOT NULL, patientId TEXT NOT NULL, consentAt INTEGER NOT NULL, PRIMARY KEY(caregiverId, patientId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_session (id INTEGER NOT NULL PRIMARY KEY, caregiverId TEXT NOT NULL, selectedPatientId TEXT)")
                db.execSQL("ALTER TABLE game_results ADD COLUMN patientId TEXT NOT NULL DEFAULT '$demo'")
                db.execSQL("UPDATE patients SET id = ?, syncId = ? WHERE id = 'patient_001'", arrayOf(demo, demo))
                db.execSQL("UPDATE reminders SET patientId = ? WHERE patientId = 'patient_001'", arrayOf(demo))
                db.execSQL("UPDATE caregiver_alerts SET patientId = ? WHERE patientId = 'patient_001'", arrayOf(demo))
                db.execSQL("UPDATE sync_queue SET entityId = ? WHERE entityId = 'patient_001'", arrayOf(demo))
            }
        }

        // Migration 6→7: Add caregiver ownership to SyncQueueEntity to prevent cross-caregiver
        // contamination. Items without an owner (NULL caregiverId) are treated as orphaned and
        // are safely skipped during sync processing. Demo/old items remain NULL and will not be
        // uploaded until their owner is explicitly set during a real caregiver session.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN caregiverId TEXT")
            }
        }

        // Migration 7→8 (Stage 3C): reminders join the caregiver-scoped SyncQueue. A queue
        // item now needs its patientId, since reminder API calls are patient-scoped and a
        // DELETE's local Room row is already gone by the time the queue is processed.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN patientId TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sahara_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
