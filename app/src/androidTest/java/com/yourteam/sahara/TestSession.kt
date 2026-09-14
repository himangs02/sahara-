package com.yourteam.sahara

import com.yourteam.sahara.auth.DemoIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Shared sign-in for instrumented tests. The app now boots into a login gate, so tests establish the
 * demo caregiver session and select the demo patient before launching the Activity, landing on the
 * elderly home exactly as the pre-account flow did.
 */
object TestSession {
    fun signInDemo(app: SaharaApplication) = runBlocking {
        // The demo caregiver, patient and link are seeded asynchronously at app startup (debug builds).
        withTimeout(15_000) { app.accountsReady.first { it } }
        app.authRepository.login(DemoIdentity.LOGIN, DemoIdentity.PASSWORD)
        app.authRepository.selectPatient(DemoIdentity.PATIENT_ID)
    }

    fun signOut(app: SaharaApplication) = runBlocking { app.authRepository.logout() }
}
