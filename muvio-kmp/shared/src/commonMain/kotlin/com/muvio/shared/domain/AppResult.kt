package com.muvio.shared.domain

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: AppException) : AppResult<Nothing>()

    val isSuccess get() = this is Success
    val isError get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrElse(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (AppException) -> Unit): AppResult<T> {
        if (this is Error) action(exception)
        return this
    }
}

fun <T> appResultOf(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: AppException) {
    AppResult.Error(e)
} catch (e: Exception) {
    AppResult.Error(e.toAppException())
}

suspend fun <T> appResultOfSuspend(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: AppException) {
    AppResult.Error(e)
} catch (e: Exception) {
    AppResult.Error(e.toAppException())
}
