package com.yourteam.sahara.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for the Stage 1/2 FastAPI auth contract (backend/app/schemas/auth.py,
 * backend/app/schemas/user.py). Field names/casing match the backend's JSON exactly. */

@Serializable
data class RegisterRequestDto(val email: String, val password: String)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserDto
)
