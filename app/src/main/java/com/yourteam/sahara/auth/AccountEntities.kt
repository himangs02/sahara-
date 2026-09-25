package com.yourteam.sahara.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [Index(value = ["login"], unique = true)])
data class AccountEntity(
    @PrimaryKey val id: String,
    val login: String,
    val passwordHash: String,
    val salt: String,
    val algorithm: String,
    val iterations: Int,
    val consentAt: Long
)

@Entity(tableName = "caregiver_patients", primaryKeys = ["caregiverId", "patientId"])
data class CaregiverPatientEntity(val caregiverId: String, val patientId: String, val consentAt: Long)

@Entity(tableName = "local_session")
data class LocalSessionEntity(@PrimaryKey val id: Int = 1, val caregiverId: String, val selectedPatientId: String? = null)

object DemoIdentity {
    const val CAREGIVER_ID = "2534493d-51e3-461e-a731-1c7b943fa302"
    const val PATIENT_ID = "40e140b1-88e7-419e-8b7d-6063ee613c02"
    const val LOGIN = "demo@sahara-app.com"
    // Public development fixture only; never a real user's password.
    const val PASSWORD = "SaharaDemo!2026"
}
