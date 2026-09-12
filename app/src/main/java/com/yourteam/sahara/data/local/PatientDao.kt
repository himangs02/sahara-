package com.yourteam.sahara.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPatient(patient: PatientEntity)

    @Query("SELECT * FROM patients WHERE id = :patientId")
    fun getPatientById(patientId: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :patientId")
    fun getPatientByIdSync(patientId: String): PatientEntity?

    @Query("SELECT * FROM patients")
    fun getAllPatients(): Flow<List<PatientEntity>>
}
