package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class ResultTest {

    @Test
    fun `Success contains data`() {
        val result: Result<String> = Result.success("hello")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.getOrNull()).isEqualTo("hello")
        assertThat(result.exceptionOrNull()).isNull()
    }

    @Test
    fun `Error contains exception`() {
        val exception = AppException.Network.NO_CONNECTION
        val result: Result<String> = Result.error(exception)

        assertThat(result.isSuccess).isFalse()
        assertThat(result.isError).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `getOrDefault returns default on error`() {
        val result: Result<Int> = Result.error(AppException.Network.TIMEOUT)

        assertThat(result.getOrDefault(42)).isEqualTo(42)
    }

    @Test
    fun `getOrDefault returns value on success`() {
        val result: Result<Int> = Result.success(100)

        assertThat(result.getOrDefault(42)).isEqualTo(100)
    }

    @Test
    fun `map transforms success value`() {
        val result: Result<Int> = Result.success(5)
        val mapped = result.map { it * 2 }

        assertThat(mapped.getOrNull()).isEqualTo(10)
    }

    @Test
    fun `map preserves error`() {
        val exception = AppException.Auth.SESSION_EXPIRED
        val result: Result<Int> = Result.error(exception)
        val mapped = result.map { it * 2 }

        assertThat(mapped.isError).isTrue()
        assertThat(mapped.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `flatMap chains operations`() {
        val result: Result<Int> = Result.success(5)
        val chained = result.flatMap { value ->
            if (value > 0) Result.success(value.toString())
            else Result.error(AppException.Unknown("Invalid value"))
        }

        assertThat(chained.getOrNull()).isEqualTo("5")
    }

    @Test
    fun `flatMap short-circuits on error`() {
        val exception = AppException.Network.NO_CONNECTION
        val result: Result<Int> = Result.error(exception)
        var called = false

        val chained = result.flatMap {
            called = true
            Result.success(it.toString())
        }

        assertThat(called).isFalse()
        assertThat(chained.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `onSuccess executes on success`() {
        var captured: String? = null
        val result: Result<String> = Result.success("test")

        result.onSuccess { captured = it }

        assertThat(captured).isEqualTo("test")
    }

    @Test
    fun `onSuccess skips on error`() {
        var captured: String? = null
        val result: Result<String> = Result.error(AppException.Network.TIMEOUT)

        result.onSuccess { captured = it }

        assertThat(captured).isNull()
    }

    @Test
    fun `onError executes on error`() {
        var captured: AppException? = null
        val exception = AppException.Auth.INVALID_CREDENTIALS
        val result: Result<String> = Result.error(exception)

        result.onError { captured = it }

        assertThat(captured).isEqualTo(exception)
    }

    @Test
    fun `onError skips on success`() {
        var captured: AppException? = null
        val result: Result<String> = Result.success("test")

        result.onError { captured = it }

        assertThat(captured).isNull()
    }

    @Test
    fun `runCatching catches exceptions`() = runTest {
        val result = com.arflix.tv.util.runCatching {
            throw IOException("Network error")
        }

        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(AppException.Unknown::class.java)
    }

    @Test
    fun `runCatching returns success on no exception`() = runTest {
        val result = com.arflix.tv.util.runCatching {
            "hello"
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("hello")
    }

    @Test
    fun `IOException converts to Network exception`() {
        val throwable: Throwable = IOException("test")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Unknown::class.java)
    }

    @Test
    fun `SocketTimeoutException converts to timeout`() {
        val throwable: Throwable = SocketTimeoutException("timeout")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Network::class.java)
        assertThat(appException.errorCode).isEqualTo("ERR_NETWORK")
    }

    @Test
    fun `SSLException converts to SSL error`() {
        val throwable: Throwable = SSLException("ssl failed")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Network::class.java)
        assertThat(appException.errorCode).isEqualTo("ERR_NETWORK")
    }

    @Test
    fun `Unknown exception converts to Unknown`() {
        val throwable: Throwable = IllegalStateException("weird error")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Unknown::class.java)
    }

    @Test
    fun `getOrElse returns value on success`() {
        val result: Result<Int> = Result.success(42)
        val value = result.getOrElse { -1 }

        assertThat(value).isEqualTo(42)
    }

    @Test
    fun `getOrElse returns fallback on error`() {
        val result: Result<Int> = Result.error(AppException.Network.TIMEOUT)
        val value = result.getOrElse { e -> -1 }

        assertThat(value).isEqualTo(-1)
    }

    @Test
    fun `getOrElse provides exception to lambda`() {
        val exception = AppException.Network.NO_CONNECTION
        val result: Result<Int> = Result.error(exception)
        var capturedError: AppException? = null

        result.getOrElse { e ->
            capturedError = e
            -1
        }

        assertThat(capturedError).isEqualTo(exception)
    }

    @Test
    fun `map returns same error when mapping error result`() {
        val result: Result<Int> = Result.error(AppException.Network.TIMEOUT)
        val mapped = result.map { it * 2 }

        assertThat(mapped).isEqualTo(result)
    }

    @Test
    fun `flatMap with nested success`() {
        val result = Result.success(10)
            .flatMap { Result.success(it + 5) }
            .flatMap { Result.success(it * 2) }

        assertThat(result.getOrNull()).isEqualTo(30)
    }

    @Test
    fun `error with message creates Unknown exception`() {
        val result: Result<String> = Result.error("Something went wrong")

        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()!!.message).isEqualTo("Something went wrong")
    }

    @Test
    fun `error with throwable converts to AppException`() {
        val result: Result<String> = Result.error(IllegalStateException("test"))

        assertThat(result.isError).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(AppException.Unknown::class.java)
    }

    @Test
    fun `UnknownHostException converts to Network exception`() {
        val throwable: Throwable = java.net.UnknownHostException("No DNS")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Network::class.java)
        assertThat(appException.message).contains("internet")
    }

    @Test
    fun `ConnectException converts to Network exception`() {
        val throwable: Throwable = java.net.ConnectException("Connection refused")
        val appException = throwable.toAppException()

        assertThat(appException).isInstanceOf(AppException.Network::class.java)
        assertThat(appException.message).contains("connect")
    }

    @Test
    fun `AppException returns itself when converted`() {
        val original = AppException.Auth.SESSION_EXPIRED
        val result = original.toAppException()

        assertThat(result).isSameInstanceAs(original)
    }

    @Test
    fun `unknown exception preserves original message`() {
        val original = RuntimeException("Custom error message")
        val converted = original.toAppException()

        assertThat(converted.message).isEqualTo("Custom error message")
    }

    @Test
    fun `exception with null message uses default`() {
        val throwable = RuntimeException(null as String?)
        val converted = throwable.toAppException()

        assertThat(converted.message).isEqualTo("An unexpected error occurred")
    }

    @Test
    fun `onSuccess returns original result`() {
        val result: Result<String> = Result.success("test")
        val returned = result.onSuccess { /* do nothing */ }

        assertThat(returned).isSameInstanceAs(result)
    }

    @Test
    fun `onError returns original result`() {
        val result: Result<String> = Result.error(AppException.Network.TIMEOUT)
        val returned = result.onError { /* do nothing */ }

        assertThat(returned).isSameInstanceAs(result)
    }

    @Test
    fun `chaining onSuccess and onError`() {
        var successValue: String? = null
        var errorValue: AppException? = null

        val result: Result<String> = Result.success("hello")
        result
            .onSuccess { successValue = it }
            .onError { errorValue = it }

        assertThat(successValue).isEqualTo("hello")
        assertThat(errorValue).isNull()
    }
}
