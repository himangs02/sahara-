package com.yourteam.sahara.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAlert(alert: CaregiverAlertEntity)

    @Update
    fun updateAlert(alert: CaregiverAlertEntity)

    @Query("UPDATE caregiver_alerts SET reviewed = 1 WHERE id = :alertId")
    fun markAlertReviewed(alertId: String)

    @Query("SELECT * FROM caregiver_alerts WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getAlertsForPatient(patientId: String): Flow<List<CaregiverAlertEntity>>
}
