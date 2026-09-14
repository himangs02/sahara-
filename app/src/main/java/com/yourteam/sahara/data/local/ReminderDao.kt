package com.yourteam.sahara.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReminder(reminder: ReminderEntity)

    /** Inserts only rows whose id is not present yet; existing reminders are left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRemindersIfAbsent(reminders: List<ReminderEntity>)

    @Update
    fun updateReminder(reminder: ReminderEntity)

    @Delete
    fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun getReminderById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY minuteOfDay ASC, createdAt ASC")
    fun getRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY minuteOfDay ASC, createdAt ASC")
    fun getRemindersForPatientSync(patientId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    fun getAllRemindersSync(): List<ReminderEntity>
}
