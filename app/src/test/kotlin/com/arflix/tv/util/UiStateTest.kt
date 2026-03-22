package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiStateTest {

    @Test
    fun `Idle state properties`() {
        val state: UiState<String> = UiState.Idle

        assertThat(state.isIdle).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.isSuccess).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.getOrNull()).isNull()
    }

    @Test
    fun `Loading state with message`() {
        val state: UiState<String> = UiState.Loading("Fetching data...")

        assertThat(state.isLoading).isTrue()
        assertThat((state as UiState.Loading).message).isEqualTo("Fetching data...")
    }

    @Test
    fun `Loading state without message`() {
        val state: UiState<String> = UiState.loading()

        assertThat(state.isLoading).isTrue()
        assertThat((state as UiState.Loading).message).isNull()
    }

    @Test
    fun `Success state contains data`() {
        val state: UiState<List<Int>> = UiState.Success(listOf(1, 2, 3))

        assertThat(state.isSuccess).isTrue()
        assertThat(state.getOrNull()).containsExactly(1, 2, 3)
    }

    @Test
    fun `Error state contains exception`() {
        val exception = AppException.Network.NO_CONNECTION
        val state: UiState<String> = UiState.Error(exception)

        assertThat(state.isError).isTrue()
        assertThat(state.exceptionOrNull()).isEqualTo(exception)
        assertThat((state as UiState.Error).message).isEqualTo("No internet connection")
        assertThat(state.errorCode).isEqualTo("ERR_NO_CONNECTION")
    }

    @Test
    fun `Error state with retry action`() {
        var retryCalled = false
        val state: UiState<String> = UiState.Error(
            AppException.Network.TIMEOUT,
            retryAction = { retryCalled = true }
        )

        assertThat((state as UiState.Error).isRetryable).isTrue()
        state.retryAction?.invoke()
        assertThat(retryCalled).isTrue()
    }

    @Test
    fun `map transforms Success data`() {
        val state: UiState<Int> = UiState.Success(5)
        val mapped = state.map { it * 2 }

        assertThat(mapped.getOrNull()).isEqualTo(10)
    }

    @Test
    fun `map preserves Loading`() {
        val state: UiState<Int> = UiState.Loading("Loading...")
        val mapped = state.map { it * 2 }

        assertThat(mapped.isLoading).isTrue()
        assertThat((mapped as UiState.Loading).message).isEqualTo("Loading...")
    }

    @Test
    fun `map preserves Error`() {
        val exception = AppException.Auth.SESSION_EXPIRED
        val state: UiState<Int> = UiState.Error(exception)
        val mapped = state.map { it * 2 }

        assertThat(mapped.isError).isTrue()
        assertThat(mapped.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `map preserves Idle`() {
        val state: UiState<Int> = UiState.Idle
        val mapped = state.map { it * 2 }

        assertThat(mapped.isIdle).isTrue()
    }

    @Test
    fun `fromResult converts Success`() {
        val result: Result<String> = Result.success("hello")
        val state = UiState.fromResult(result)

        assertThat(state.isSuccess).isTrue()
        assertThat(state.getOrNull()).isEqualTo("hello")
    }

    @Test
    fun `fromResult converts Error`() {
        val exception = AppException.Server.notFound()
        val result: Result<String> = Result.error(exception)
        val state = UiState.fromResult(result)

        assertThat(state.isError).isTrue()
        assertThat(state.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `toUiState extension converts Result`() {
        val result: Result<Int> = Result.success(42)
        val state = result.toUiState()

        assertThat(state.isSuccess).isTrue()
        assertThat(state.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `success factory creates Success state`() {
        val state: UiState<String> = UiState.success("data")

        assertThat(state).isInstanceOf(UiState.Success::class.java)
        assertThat(state.getOrNull()).isEqualTo("data")
    }

    @Test
    fun `error factory creates Error state`() {
        val exception = AppException.Unknown("test")
        val state: UiState<String> = UiState.error(exception)

        assertThat(state).isInstanceOf(UiState.Error::class.java)
        assertThat(state.exceptionOrNull()).isEqualTo(exception)
    }

    @Test
    fun `Error isRetryable delegates to AppException`() {
        val networkError = UiState.Error(AppException.Network.TIMEOUT)
        val authError = UiState.Error(AppException.Auth.SESSION_EXPIRED)

        assertThat(networkError.isRetryable).isTrue()
        assertThat(authError.isRetryable).isFalse()
    }

    @Test
    fun `Error message delegates to AppException`() {
        val error = UiState.Error(AppException.Network("Custom network error"))

        assertThat(error.message).isEqualTo("Custom network error")
    }

    @Test
    fun `Error errorCode delegates to AppException`() {
        val error = UiState.Error(AppException.Network.NO_CONNECTION)

        assertThat(error.errorCode).isEqualTo("ERR_NO_CONNECTION")
    }

    @Test
    fun `map transforms through nested data`() {
        val state: UiState<List<Int>> = UiState.Success(listOf(1, 2, 3))
        val mapped = state.map { it.sum() }

        assertThat(mapped.getOrNull()).isEqualTo(6)
    }

    @Test
    fun `getOrNull returns null for Loading`() {
        val state: UiState<String> = UiState.Loading()

        assertThat(state.getOrNull()).isNull()
    }

    @Test
    fun `getOrNull returns null for Idle`() {
        val state: UiState<String> = UiState.Idle

        assertThat(state.getOrNull()).isNull()
    }

    @Test
    fun `exceptionOrNull returns null for Success`() {
        val state: UiState<String> = UiState.Success("test")

        assertThat(state.exceptionOrNull()).isNull()
    }

    @Test
    fun `exceptionOrNull returns null for Loading`() {
        val state: UiState<String> = UiState.Loading()

        assertThat(state.exceptionOrNull()).isNull()
    }

    @Test
    fun `exceptionOrNull returns null for Idle`() {
        val state: UiState<String> = UiState.Idle

        assertThat(state.exceptionOrNull()).isNull()
    }

    @Test
    fun `error with retry action can be invoked`() {
        var retryCalled = false
        val state: UiState<String> = UiState.error(
            AppException.Network.TIMEOUT,
            retryAction = { retryCalled = true }
        )

        (state as UiState.Error).retryAction?.invoke()

        assertThat(retryCalled).isTrue()
    }

    @Test
    fun `toUiState extension with retry action`() {
        var retryCalled = false
        val result: Result<String> = Result.error(AppException.Network.NO_CONNECTION)
        val state = result.toUiState { retryCalled = true }

        (state as UiState.Error).retryAction?.invoke()

        assertThat(retryCalled).isTrue()
    }

    @Test
    fun `map preserves Error retry action`() {
        var retryCalled = false
        val state: UiState<Int> = UiState.Error(
            AppException.Network.TIMEOUT,
            retryAction = { retryCalled = true }
        )
        val mapped = state.map { it * 2 }

        (mapped as UiState.Error).retryAction?.invoke()

        assertThat(retryCalled).isTrue()
    }

    @Test
    fun `Loading with custom message`() {
        val state: UiState<String> = UiState.loading("Loading movies...")

        assertThat(state.isLoading).isTrue()
        assertThat((state as UiState.Loading).message).isEqualTo("Loading movies...")
    }

    @Test
    fun `Success can hold nullable data`() {
        val state: UiState<String?> = UiState.Success(null)

        assertThat(state.isSuccess).isTrue()
        assertThat(state.getOrNull()).isNull()
    }

    @Test
    fun `Success can hold complex types`() {
        data class ComplexData(val items: List<String>, val count: Int)
        val data = ComplexData(listOf("a", "b"), 2)
        val state: UiState<ComplexData> = UiState.Success(data)

        assertThat(state.getOrNull()).isEqualTo(data)
    }

    @Test
    fun `state equality works correctly`() {
        val state1: UiState<String> = UiState.Success("test")
        val state2: UiState<String> = UiState.Success("test")
        val state3: UiState<String> = UiState.Success("different")

        assertThat(state1).isEqualTo(state2)
        assertThat(state1).isNotEqualTo(state3)
    }

    @Test
    fun `Idle is singleton`() {
        val idle1: UiState<String> = UiState.Idle
        val idle2: UiState<Int> = UiState.Idle

        assertThat(idle1).isSameInstanceAs(idle2)
    }
}
