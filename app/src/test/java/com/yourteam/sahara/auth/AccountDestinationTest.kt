package com.yourteam.sahara.auth

import com.yourteam.sahara.model.Patient
import org.junit.Assert.assertEquals
import org.junit.Test

/** The account gate's rules: what an unauthenticated, logged-out or mis-selected session may reach. */
class AccountDestinationTest {

    private val caregiver = "caregiver-1"
    private val kamala = Patient(id = "patient-a", name = "Kamala Devi")
    private val other = Patient(id = "patient-b", name = "Someone Else")

    @Test fun `nothing is shown until startup and session restore finish`() {
        assertEquals(AccountDestination.Loading, resolveAccountDestination(false, null, null))
        assertEquals(AccountDestination.Loading, resolveAccountDestination(false, LocalSessionEntity(caregiverId = caregiver, selectedPatientId = kamala.id), listOf(kamala)))
    }

    @Test fun `unauthenticated users can only reach login`() {
        assertEquals(AccountDestination.Login, resolveAccountDestination(true, null, null))
        // Even a leftover patient list never opens patient screens without a session.
        assertEquals(AccountDestination.Login, resolveAccountDestination(true, null, listOf(kamala)))
    }

    @Test fun `logout blocks protected navigation`() {
        val inPatientHome = resolveAccountDestination(true, LocalSessionEntity(caregiverId = caregiver, selectedPatientId = kamala.id), listOf(kamala))
        assertEquals(AccountDestination.PatientHome(kamala.id), inPatientHome)

        // Logging out clears the session; the gate then removes every authenticated screen.
        assertEquals(AccountDestination.Login, resolveAccountDestination(true, null, listOf(kamala)))
    }

    @Test fun `a logged-in caregiver without a selection chooses a patient`() {
        assertEquals(AccountDestination.PatientSelection, resolveAccountDestination(true, LocalSessionEntity(caregiverId = caregiver), listOf(kamala)))
    }

    @Test fun `the patient list is awaited before opening a selected patient`() {
        assertEquals(AccountDestination.Loading, resolveAccountDestination(true, LocalSessionEntity(caregiverId = caregiver, selectedPatientId = kamala.id), null))
    }

    @Test fun `a selected patient that is not linked to this caregiver is never opened`() {
        val session = LocalSessionEntity(caregiverId = caregiver, selectedPatientId = other.id)
        assertEquals(AccountDestination.PatientSelection, resolveAccountDestination(true, session, listOf(kamala)))
    }
}
