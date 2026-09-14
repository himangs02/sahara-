package com.yourteam.sahara.api

import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A backend call failure, categorized so the UI can show one simple, elderly-friendly message
 * per category instead of a raw exception message or a server stack trace. */
sealed class BackendError {
    /** No network route at all (airplane mode, DNS failure, Wi-Fi off). */
    data object NetworkUnavailable : BackendError()

    /** A route exists but nothing is listening (backend not running, wrong port). */
    data object ConnectionRefused : BackendError()

    /** The server didn't respond within the client's timeout. */
    data object Timeout : BackendError()

    /** 401 -- the credentials, or the stored token, were rejected. */
    data object Unauthorized : BackendError()

    /** 404 -- the requested resource doesn't exist (or isn't linked to this caregiver). */
    data object NotFound : BackendError()

    /** 422 -- the request body failed backend validation (e.g. too-short password). */
    data class ValidationError(val detail: String?) : BackendError()

    /** 409 -- e.g. registering an email that's already taken. */
    data object Conflict : BackendError()

    /** 5xx -- the backend itself failed. Never surface the body: it may carry internals. */
    data object ServerError : BackendError()

    data class Unknown(val detail: String?) : BackendError()
}

/** Maps any exception a Retrofit call can throw to a [BackendError]. Deliberately never includes
 * the raw exception message for HTTP error bodies -- those can carry backend internals -- except
 * for [BackendError.ValidationError], whose detail is the backend's own intentionally-public
 * "why this request is invalid" message. */
fun Throwable.toBackendError(): BackendError = when (this) {
    is UnknownHostException -> BackendError.NetworkUnavailable
    is ConnectException -> BackendError.ConnectionRefused
    is SocketTimeoutException -> BackendError.Timeout
    is HttpException -> when (code()) {
        401 -> BackendError.Unauthorized
        404 -> BackendError.NotFound
        409 -> BackendError.Conflict
        422 -> BackendError.ValidationError(response()?.errorBody()?.string())
        in 500..599 -> BackendError.ServerError
        else -> BackendError.Unknown("HTTP ${code()}")
    }
    else -> BackendError.Unknown(message)
}
