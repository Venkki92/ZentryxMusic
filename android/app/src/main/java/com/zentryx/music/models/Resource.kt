package com.zentryx.music.models

/**
 * Generic result wrapper for asynchronous operations and network calls.
 */
sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val errorType: ErrorType, val message: String, val cause: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
}

/**
 * Specific error classification to distinguish connection failures.
 */
enum class ErrorType {
    BAD_URL,
    NO_NETWORK,
    SSL_ERROR,
    UNAUTHORIZED,          // Invalid username / password
    WEBDAV_UNREACHABLE,    // Server returned 404 or 5xx for remote.php/dav
    TIMEOUT,
    UNKNOWN
}
