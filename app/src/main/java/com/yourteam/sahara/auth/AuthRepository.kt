package com.yourteam.sahara.auth

import com.yourteam.sahara.model.Patient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Local prototype authentication: salted password verifiers and a session row stored on this device.
 * It is deliberately isolated behind [AuthDataSource] so a real backend can replace it later, and it is
 * not production-grade security (no server verification, no encrypted storage, no rate limiting).
 */
class AuthRepository(private val source: AuthDataSource, private val hasher: PasswordHasher = PasswordHasher()) {
    private val lock = Mutex()

    /** The active session, or null. A session whose account no longer exists counts as logged out. */
    val sessions: Flow<LocalSessionEntity?> = source.sessions.map { session ->
        session?.takeIf { source.accountById(it.caregiverId) != null }
    }

    suspend fun login(login: String, password: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (login.isBlank() || password.isEmpty()) throw AuthException(AuthFailure.MISSING_CREDENTIALS)
            // Same failure for an unknown login and a wrong password, so logins cannot be probed.
            if (password.length > MAX_PASSWORD) throw AuthException(AuthFailure.INVALID_CREDENTIALS)
            val account = source.account(normalize(login))
            if (account == null || !hasher.verify(password, account)) throw AuthException(AuthFailure.INVALID_CREDENTIALS)
            source.saveSession(LocalSessionEntity(caregiverId = account.id))
        }
    }

    suspend fun register(login: String, password: String, consent: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            val normalized = normalize(login)
            if (normalized.length !in 3..100 || normalized.any { it.isWhitespace() }) throw AuthException(AuthFailure.INVALID_LOGIN_FORMAT)
            if (password.length !in MIN_PASSWORD..MAX_PASSWORD) throw AuthException(AuthFailure.WEAK_PASSWORD)
            if (!consent) throw AuthException(AuthFailure.CONSENT_REQUIRED)
            if (source.account(normalized) != null) throw AuthException(AuthFailure.LOGIN_TAKEN)
            val verifier = hasher.hash(password)
            val account = AccountEntity(
                UUID.randomUUID().toString(), normalized, verifier.hash, verifier.salt,
                verifier.algorithm, verifier.iterations, System.currentTimeMillis()
            )
            source.insertAccount(account)
            source.saveSession(LocalSessionEntity(caregiverId = account.id))
        }
    }

    suspend fun logout() = lock.withLock { source.clearSession() }

    suspend fun restoreSession(): LocalSessionEntity? = sessions.first()

    /** Throws [PatientAccessDeniedException] unless a caregiver is logged in, linked to and viewing [patientId]. */
    suspend fun requirePatient(patientId: String) {
        val session = source.session() ?: throw PatientAccessDeniedException("not logged in")
        if (source.accountById(session.caregiverId) == null) throw PatientAccessDeniedException("account missing")
        if (!source.isLinked(session.caregiverId, patientId)) throw PatientAccessDeniedException("patient not linked")
        if (session.selectedPatientId != patientId) throw PatientAccessDeniedException("patient not selected")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun patients(): Flow<List<Patient>> = sessions.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else source.patients(session.caregiverId)
    }

    suspend fun selectPatient(patientId: String?) = lock.withLock {
        val session = source.session() ?: throw AuthException(AuthFailure.SESSION_REQUIRED)
        if (patientId != null && !source.isLinked(session.caregiverId, patientId)) throw AuthException(AuthFailure.PATIENT_ACCESS_DENIED)
        source.saveSession(session.copy(selectedPatientId = patientId))
    }

    /** Creates a patient linked to the logged-in caregiver ([id] null) or edits one they are linked to. */
    suspend fun savePatient(id: String?, name: String, age: Int, region: String, language: String, consent: Boolean): Patient =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val session = source.session() ?: throw AuthException(AuthFailure.SESSION_REQUIRED)
                if (name.trim().length !in 1..100) throw AuthException(AuthFailure.INVALID_PATIENT_NAME)
                if (age !in 0..120) throw AuthException(AuthFailure.INVALID_PATIENT_AGE)
                if (region.trim().length > 100) throw AuthException(AuthFailure.INVALID_PATIENT_REGION)
                if (language !in SUPPORTED_LANGUAGES) throw AuthException(AuthFailure.UNSUPPORTED_LANGUAGE)
                val patientId = id ?: UUID.randomUUID().toString()
                val patient = Patient(id = patientId, syncId = patientId, name = name.trim(), age = age, region = region.trim(), language = language)
                if (id == null) {
                    if (!consent) throw AuthException(AuthFailure.CONSENT_REQUIRED)
                    source.createPatient(session.caregiverId, patient, System.currentTimeMillis())
                } else {
                    if (!source.isLinked(session.caregiverId, id)) throw AuthException(AuthFailure.PATIENT_ACCESS_DENIED)
                    source.updatePatient(patient)
                }
                patient
            }
        }

    private fun normalize(login: String) = login.trim().lowercase(Locale.ROOT)

    companion object {
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 128
        /** Stored names; the UI shows each in its own script. */
        val SUPPORTED_LANGUAGES = listOf("English", "Hindi", "Assamese")
    }
}
