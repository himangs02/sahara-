package com.yourteam.sahara.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {

    private val source = FakeAuthDataSource()
    private val repo = AuthRepository(source)

    private fun registerCaregiver() = runBlocking { repo.register("caregiver@home", "correct horse battery", consent = true) }

    private fun expectFailure(expected: AuthFailure, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("Expected $expected")
        } catch (e: AuthException) {
            assertEquals(expected, e.failure)
        }
    }

    private fun expectAccessDenied(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("Expected patient access to be denied")
        } catch (_: PatientAccessDeniedException) {
        }
    }

    @Test fun `registration creates an account, hashes the password and starts a session`() = runBlocking {
        repo.register("Caregiver@Home", "correct horse battery", consent = true)

        val account = source.account("caregiver@home")
        assertNotNull("Login is stored normalized", account)
        assertFalse("Password is never stored in plaintext", account!!.passwordHash.contains("correct horse battery"))
        assertTrue(account.passwordHash.isNotBlank() && account.salt.isNotBlank())
        assertTrue(account.iterations > 1)
        assertNotNull("Registration logs the caregiver in", repo.sessions.first())
    }

    @Test fun `two accounts with the same password get different salts and hashes`() = runBlocking {
        repo.register("first@home", "correct horse battery", consent = true)
        repo.register("second@home", "correct horse battery", consent = true)
        val first = source.account("first@home")!!
        val second = source.account("second@home")!!
        assertTrue(first.salt != second.salt && first.passwordHash != second.passwordHash)
    }

    @Test fun `registration requires consent, a valid login and a strong password`() {
        expectFailure(AuthFailure.CONSENT_REQUIRED) { repo.register("caregiver@home", "correct horse battery", consent = false) }
        expectFailure(AuthFailure.WEAK_PASSWORD) { repo.register("caregiver@home", "short", consent = true) }
        expectFailure(AuthFailure.INVALID_LOGIN_FORMAT) { repo.register("has space", "correct horse battery", consent = true) }
        expectFailure(AuthFailure.INVALID_LOGIN_FORMAT) { repo.register("ab", "correct horse battery", consent = true) }
        assertNull("Failed registration starts no session", runBlocking { repo.sessions.first() })
    }

    @Test fun `duplicate registration on the same device is rejected`() {
        registerCaregiver()
        expectFailure(AuthFailure.LOGIN_TAKEN) { repo.register("CAREGIVER@home", "another password", consent = true) }
    }

    @Test fun `valid login succeeds regardless of login case`() = runBlocking {
        registerCaregiver()
        repo.logout()
        repo.login("  CAREGIVER@HOME ", "correct horse battery")
        assertEquals(source.account("caregiver@home")!!.id, repo.sessions.first()?.caregiverId)
    }

    @Test fun `invalid login is rejected with one generic reason and creates no session`() {
        registerCaregiver()
        runBlocking { repo.logout() }
        expectFailure(AuthFailure.INVALID_CREDENTIALS) { repo.login("caregiver@home", "wrong password") }
        // An unknown account gets the same answer, so logins cannot be probed.
        expectFailure(AuthFailure.INVALID_CREDENTIALS) { repo.login("nobody@home", "correct horse battery") }
        expectFailure(AuthFailure.MISSING_CREDENTIALS) { repo.login("", "") }
        assertNull(runBlocking { repo.sessions.first() })
    }

    @Test fun `logout clears the session`() = runBlocking {
        registerCaregiver()
        assertNotNull(repo.sessions.first())
        repo.logout()
        assertNull(repo.sessions.first())
        assertNull(source.session())
    }

    @Test fun `a session persists and is restored by a new repository over the same storage`() = runBlocking {
        registerCaregiver()
        val patient = repo.savePatient(null, "Kamala Devi", 74, "Assam", "Assamese", consent = true)
        repo.selectPatient(patient.id)

        // A fresh repository simulates the app process restarting against the same database.
        val restarted = AuthRepository(source)
        val restored = restarted.restoreSession()
        assertNotNull("Closing and reopening keeps the caregiver logged in", restored)
        assertEquals(source.account("caregiver@home")!!.id, restored!!.caregiverId)
        assertEquals("The selected patient is restored too", patient.id, restored.selectedPatientId)
        restarted.requirePatient(patient.id)
    }

    @Test fun `a session for an account that no longer exists is treated as logged out`() = runBlocking {
        registerCaregiver()
        source.saveSession(LocalSessionEntity(caregiverId = "ghost-account"))
        assertNull("An unknown caregiver id must not be accepted", repo.sessions.first())
        expectAccessDenied { repo.requirePatient("any-patient") }
    }

    @Test fun `creating a patient links it to the caregiver and lists it`() = runBlocking {
        registerCaregiver()
        val patient = repo.savePatient(null, "Kamala Devi", 74, "Assam", "Assamese", consent = true)

        assertEquals(listOf("Kamala Devi"), repo.patients().first().map { it.name })
        assertEquals(patient.id, patient.syncId)
        assertTrue(source.isLinked(source.account("caregiver@home")!!.id, patient.id))
    }

    @Test fun `each caregiver sees only their own patients`() = runBlocking {
        repo.register("first@home", "correct horse battery", consent = true)
        repo.savePatient(null, "First's patient", 70, "Assam", "Assamese", consent = true)
        repo.logout()

        repo.register("second@home", "correct horse battery", consent = true)
        repo.savePatient(null, "Second's patient", 71, "Assam", "Hindi", consent = true)

        assertEquals(listOf("Second's patient"), repo.patients().first().map { it.name })
    }

    @Test fun `editing a patient keeps its id and does not require consent again`() = runBlocking {
        registerCaregiver()
        val patient = repo.savePatient(null, "Kamala", 74, "Assam", "Assamese", consent = true)
        val edited = repo.savePatient(patient.id, "Kamala Devi", 75, "Assam", "Hindi", consent = false)

        assertEquals(patient.id, edited.id)
        val listed = repo.patients().first().single()
        assertEquals("Kamala Devi", listed.name)
        assertEquals(75, listed.age)
        assertEquals("Hindi", listed.language)
    }

    @Test fun `patient details are validated`() {
        registerCaregiver()
        expectFailure(AuthFailure.INVALID_PATIENT_NAME) { repo.savePatient(null, "  ", 74, "Assam", "Assamese", consent = true) }
        expectFailure(AuthFailure.INVALID_PATIENT_AGE) { repo.savePatient(null, "Kamala", 200, "Assam", "Assamese", consent = true) }
        expectFailure(AuthFailure.INVALID_PATIENT_AGE) { repo.savePatient(null, "Kamala", -1, "Assam", "Assamese", consent = true) }
        expectFailure(AuthFailure.UNSUPPORTED_LANGUAGE) { repo.savePatient(null, "Kamala", 74, "Assam", "French", consent = true) }
        expectFailure(AuthFailure.CONSENT_REQUIRED) { repo.savePatient(null, "Kamala", 74, "Assam", "Assamese", consent = false) }
        assertTrue(runBlocking { repo.patients().first() }.isEmpty())
    }

    @Test fun `a caregiver cannot select or edit a patient they are not linked to`() {
        registerCaregiver()
        expectFailure(AuthFailure.PATIENT_ACCESS_DENIED) { repo.selectPatient("someone-elses-patient") }
        expectFailure(AuthFailure.PATIENT_ACCESS_DENIED) { repo.savePatient("someone-elses-patient", "X", 70, "", "English", consent = true) }
    }

    @Test fun `patient management requires a session`() {
        expectFailure(AuthFailure.SESSION_REQUIRED) { repo.savePatient(null, "Kamala", 74, "Assam", "Assamese", consent = true) }
        expectFailure(AuthFailure.SESSION_REQUIRED) { repo.selectPatient(null) }
    }

    @Test fun `requirePatient enforces login, linkage and active selection`() = runBlocking {
        registerCaregiver()
        val a = repo.savePatient(null, "Patient A", 70, "Assam", "Assamese", consent = true)
        val b = repo.savePatient(null, "Patient B", 72, "Assam", "Hindi", consent = true)

        // Logged in, but no patient is selected yet.
        expectAccessDenied { repo.requirePatient(a.id) }

        repo.selectPatient(a.id)
        repo.requirePatient(a.id)
        expectAccessDenied { repo.requirePatient(b.id) }

        // After logout, no patient data is reachable.
        repo.logout()
        expectAccessDenied { repo.requirePatient(a.id) }
    }
}
