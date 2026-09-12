package com.yourteam.sahara.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GameResultEntity::class,
        PatientEntity::class,
        SyncQueueEntity::class,
        ReminderEntity::class,
        CaregiverAlertEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sahara_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
