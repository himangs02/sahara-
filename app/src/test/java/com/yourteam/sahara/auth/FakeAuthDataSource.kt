package com.yourteam.sahara.auth

import com.yourteam.sahara.model.Patient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [AuthDataSource] that mirrors the Room implementation's behaviour: accounts keyed by id
 * with a unique login, caregiver→patient links, one persisted session row, and a patient table.
 * State survives across [AuthRepository] instances, so it can stand in for on-device persistence.
 */
class FakeAuthDataSource : AuthDataSource {
    private val accounts = LinkedHashMap<String, AccountEntity>()
    private val links = mutableSetOf<Pair<String, String>>()
    private val patientsById = MutableStateFlow<Map<String, Patient>>(emptyMap())
    private val sessionState = MutableStateFlow<LocalSessionEntity?>(null)

    override val sessions: Flow<LocalSessionEntity?> = sessionState
    override suspend fun session() = sessionState.value
    override suspend fun account(login: String) = accounts.values.find { it.login == login }
    override suspend fun accountById(id: String) = accounts[id]
    override suspend fun insertAccount(account: AccountEntity) {
        require(accounts.values.none { it.login == account.login }) { "login already exists" }
        accounts[account.id] = account
    }
    override suspend fun saveSession(session: LocalSessionEntity) { sessionState.value = session.copy(id = 1) }
    override suspend fun clearSession() { sessionState.value = null }
    override suspend fun isLinked(caregiverId: String, patientId: String) = (caregiverId to patientId) in links
    override fun patients(caregiverId: String): Flow<List<Patient>> = patientsById.map { all ->
        links.filter { it.first == caregiverId }.mapNotNull { all[it.second] }.sortedBy { it.name }
    }
    override suspend fun createPatient(caregiverId: String, patient: Patient, consentAt: Long) {
        check(accounts.containsKey(caregiverId))
        check(!patientsById.value.containsKey(patient.id)) { "patient already exists" }
        patientsById.value = patientsById.value + (patient.id to patient)
        links.add(caregiverId to patient.id)
    }
    override suspend fun updatePatient(patient: Patient) {
        patientsById.value = patientsById.value + (patient.id to patient)
    }
}
