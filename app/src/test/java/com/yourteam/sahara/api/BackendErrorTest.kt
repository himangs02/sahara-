package com.yourteam.sahara.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Every category the Stage 3 error-handling requirement lists: network unavailable, connection
 * refused, timeout, 401, 404, 422, 500, and an unrecognized failure. */
class BackendErrorTest {

    private fun httpException(code: Int, body: String = "") =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test fun `unknown host maps to network unavailable`() {
        assertEquals(BackendError.NetworkUnavailable, UnknownHostException().toBackendError())
    }

    @Test fun `connect exception maps to connection refused`() {
        assertEquals(BackendError.ConnectionRefused, ConnectException().toBackendError())
    }

    @Test fun `socket timeout maps to timeout`() {
        assertEquals(BackendError.Timeout, SocketTimeoutException().toBackendError())
    }

    @Test fun `http 401 maps to unauthorized`() {
        assertEquals(BackendError.Unauthorized, httpException(401).toBackendError())
    }

    @Test fun `http 404 maps to not found`() {
        assertEquals(BackendError.NotFound, httpException(404).toBackendError())
    }

    @Test fun `http 409 maps to conflict`() {
        assertEquals(BackendError.Conflict, httpException(409).toBackendError())
    }

    @Test fun `http 422 maps to validation error and keeps the backend detail`() {
        val error = httpException(422, """{"detail":"password too short"}""").toBackendError()
        assertTrue(error is BackendError.ValidationError)
        assertTrue((error as BackendError.ValidationError).detail?.contains("password too short") == true)
    }

    @Test fun `http 500 maps to server error`() {
        assertEquals(BackendError.ServerError, httpException(500).toBackendError())
    }

    @Test fun `http 503 also maps to server error`() {
        assertEquals(BackendError.ServerError, httpException(503).toBackendError())
    }

    @Test fun `an unrecognized failure maps to unknown, never crashes the mapper`() {
        val error = RuntimeException("something unexpected").toBackendError()
        assertTrue(error is BackendError.Unknown)
    }
}
