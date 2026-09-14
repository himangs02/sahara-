package com.yourteam.sahara.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for backend/app/schemas/patient.py. */

@Serializable
data class PatientCreateDto(
    val id: String? = null,
    val name: String,
    val age: Int,
    @SerialName("preferred_language") val preferredLanguage: String,
    val region: String
)

@Serializable
data class PatientUpdateDto(
    val name: String? = null,
    val age: Int? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    val region: String? = null
)

@Serializable
data class PatientDto(
    val id: String,
    val name: String,
    val age: Int,
    @SerialName("preferred_language") val preferredLanguage: String,
    val region: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
