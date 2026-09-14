package com.yourteam.sahara.auth

import androidx.room.*
import com.yourteam.sahara.data.local.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {
    @Query("SELECT * FROM accounts WHERE login = :login LIMIT 1")
    suspend fun account(login: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun accountById(id: String): AccountEntity?
    @Insert suspend fun insertAccount(account: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(link: CaregiverPatientEntity)
    @Query("SELECT * FROM local_session WHERE id = 1")
    fun observeSession(): Flow<LocalSessionEntity?>
    @Query("SELECT * FROM local_session WHERE id = 1")
    suspend fun session(): LocalSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(session: LocalSessionEntity)
    @Query("DELETE FROM local_session") suspend fun clearSession()
    @Query("SELECT EXISTS(SELECT 1 FROM caregiver_patients WHERE caregiverId = :caregiverId AND patientId = :patientId)")
    suspend fun isLinked(caregiverId: String, patientId: String): Boolean
    @Query("SELECT p.* FROM patients p INNER JOIN caregiver_patients c ON c.patientId = p.id WHERE c.caregiverId = :caregiverId ORDER BY p.name")
    fun patients(caregiverId: String): Flow<List<PatientEntity>>
}
