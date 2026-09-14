package com.yourteam.sahara.auth

import com.yourteam.sahara.model.Patient

/** Why an account or patient-management action was refused. The UI maps each to a translated message. */
enum class AuthFailure {
    MISSING_CREDENTIALS,
    INVALID_CREDENTIALS,
    INVALID_LOGIN_FORMAT,
    WEAK_PASSWORD,
    CONSENT_REQUIRED,
    LOGIN_TAKEN,
    INVALID_PATIENT_NAME,
    INVALID_PATIENT_AGE,
    INVALID_PATIENT_REGION,
    UNSUPPORTED_LANGUAGE,
    SESSION_REQUIRED,
    PATIENT_ACCESS_DENIED
}

/** A user-correctable failure from login, registration or patient management. */
class AuthException(val failure: AuthFailure) : Exception(failure.name)

/**
 * Thrown by patient-scoped data access when the current session may not read or write that patient.
 * This is a guard against wrong routes or stale screens, not a replacement for server-side security.
 */
class PatientAccessDeniedException(reason: String) : SecurityException("Patient access denied: $reason")

/** What the account gate should show. Kept pure so the protection rules can be unit tested. */
sealed interface AccountDestination {
    data object Loading : AccountDestination
    data object Login : AccountDestination
    data object PatientSelection : AccountDestination
    data class PatientHome(val patientId: String) : AccountDestination
}

/**
 * - Nothing protected is shown until seeding and session restore finish.
 * - No session always means Login, which is what makes logout remove every authenticated screen.
 * - A selected patient opens only if it is one of this caregiver's linked patients.
 */
fun resolveAccountDestination(ready: Boolean, session: LocalSessionEntity?, linkedPatients: List<Patient>?): AccountDestination {
    if (!ready) return AccountDestination.Loading
    if (session == null) return AccountDestination.Login
    if (linkedPatients == null) return AccountDestination.Loading
    val selected = session.selectedPatientId ?: return AccountDestination.PatientSelection
    return if (linkedPatients.any { it.id == selected }) AccountDestination.PatientHome(selected) else AccountDestination.PatientSelection
}
