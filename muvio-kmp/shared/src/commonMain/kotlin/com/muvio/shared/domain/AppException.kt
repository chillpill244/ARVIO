package com.muvio.shared.domain

sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null,
    open val errorCode: String? = null,
) : Exception(message, cause) {

    data class Network(
        override val message: String,
        override val cause: Throwable? = null,
        override val errorCode: String? = "ERR_NETWORK",
    ) : AppException(message, cause, errorCode) {
        companion object {
            val NO_CONNECTION = Network("No internet connection", errorCode = "ERR_NO_CONNECTION")
            val TIMEOUT = Network("Connection timed out", errorCode = "ERR_TIMEOUT")
        }
    }

    data class Auth(
        override val message: String,
        override val cause: Throwable? = null,
        override val errorCode: String? = "ERR_AUTH",
    ) : AppException(message, cause, errorCode) {
        companion object {
            val SESSION_EXPIRED = Auth("Session expired. Please sign in again.", errorCode = "ERR_SESSION_EXPIRED")
            val INVALID_CREDENTIALS = Auth("Invalid email or password", errorCode = "ERR_INVALID_CREDENTIALS")
        }
    }

    data class Server(
        override val message: String,
        val httpCode: Int,
        override val cause: Throwable? = null,
        override val errorCode: String? = "ERR_SERVER",
    ) : AppException(message, cause, errorCode)

    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
        override val errorCode: String? = "ERR_UNKNOWN",
    ) : AppException(message, cause, errorCode)

    fun isRetryable(): Boolean = when (this) {
        is Network -> true
        is Server -> httpCode in 500..599
        is Auth, is Unknown -> false
    }

    fun toSupportString(): String =
        if (errorCode != null) "$message [$errorCode]" else message
}

fun Throwable.toAppException(): AppException = when (this) {
    is AppException -> this
    else -> AppException.Unknown(message ?: "Unexpected error", cause = this)
}
