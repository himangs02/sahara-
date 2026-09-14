package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.LoginRequestDto
import com.yourteam.sahara.api.model.RegisterRequestDto
import com.yourteam.sahara.api.model.TokenResponseDto
import com.yourteam.sahara.api.model.UserDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Confirms Android's DTOs speak the exact JSON shape FastAPI produces/expects
 * (backend/app/schemas/auth.py, backend/app/schemas/user.py) -- snake_case field
 * names in the wire format, camelCase in Kotlin. */
class AuthDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `register request serializes with plain field names`() {
        val body = json.encodeToString(RegisterRequestDto.serializer(), RegisterRequestDto("a@example.com", "password123"))
        assertEquals("""{"email":"a@example.com","password":"password123"}""", body)
    }

    @Test fun `login request serializes with plain field names`() {
        val body = json.encodeToString(LoginRequestDto.serializer(), LoginRequestDto("a@example.com", "password123"))
        assertEquals("""{"email":"a@example.com","password":"password123"}""", body)
    }

    @Test fun `token response deserializes the real backend shape, snake_case included`() {
        val backendJson = """
            {
                "access_token": "abc.def.ghi",
                "token_type": "bearer",
                "user": {
                    "id": "1534493d-51e3-461e-a731-1c7b943fa301",
                    "email": "caregiver@example.com",
                    "is_active": true,
                    "created_at": "2026-09-14T00:00:00Z"
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString(TokenResponseDto.serializer(), backendJson)

        assertEquals("abc.def.ghi", parsed.accessToken)
        assertEquals("bearer", parsed.tokenType)
        assertEquals("1534493d-51e3-461e-a731-1c7b943fa301", parsed.user.id)
        assertEquals("caregiver@example.com", parsed.user.email)
        assertEquals(true, parsed.user.isActive)
    }

    @Test fun `user dto deserializes the auth me response shape`() {
        val backendJson = """{"id":"abc","email":"me@example.com","is_active":false,"created_at":"2026-09-14T00:00:00Z"}"""
        val parsed = json.decodeFromString(UserDto.serializer(), backendJson)
        assertEquals("abc", parsed.id)
        assertEquals("me@example.com", parsed.email)
        assertEquals(false, parsed.isActive)
    }

    @Test fun `unknown extra fields from the backend are ignored, not fatal`() {
        val backendJson = """{"id":"abc","email":"me@example.com","is_active":true,"created_at":"now","some_future_field":123}"""
        val parsed = json.decodeFromString(UserDto.serializer(), backendJson)
        assertEquals("abc", parsed.id)
    }
}
