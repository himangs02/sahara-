package com.yourteam.sahara.auth

import androidx.room.withTransaction
import com.yourteam.sahara.data.local.AppDatabase
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.Patient
import kotlinx.coroutines.flow.map

class RoomAuthDataSource(private val db: AppDatabase) : AuthDataSource {
    private val dao get() = db.authDao()
    override val sessions get() = dao.observeSession()
    override suspend fun session() = dao.session()
    override suspend fun account(login: String) = dao.account(login)
    override suspend fun accountById(id: String) = dao.accountById(id)
    override suspend fun insertAccount(account: AccountEntity) = dao.insertAccount(account)
    override suspend fun saveSession(session: LocalSessionEntity) = dao.saveSession(session)
    override suspend fun clearSession() = dao.clearSession()
    override suspend fun isLinked(caregiverId: String, patientId: String) = dao.isLinked(caregiverId, patientId)
    override fun patients(caregiverId: String) = dao.patients(caregiverId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun createPatient(caregiverId: String, patient: Patient, consentAt: Long) = db.withTransaction {
        check(dao.accountById(caregiverId) != null)
        check(db.patientDao().getPatientByIdSync(patient.id) == null)
        db.patientDao().insertPatient(patient.toEntity())
        dao.link(CaregiverPatientEntity(caregiverId, patient.id, consentAt))
    }
    override suspend fun updatePatient(patient: Patient) { db.patientDao().insertPatient(patient.toEntity()) }
}
