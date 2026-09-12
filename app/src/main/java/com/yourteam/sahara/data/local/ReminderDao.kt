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

    @Update
    fun updateReminder(reminder: ReminderEntity)

    @Delete
    fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY timeMillis ASC")
    fun getRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY timeMillis ASC")
    fun getRemindersForPatientSync(patientId: String): List<ReminderEntity>
}
