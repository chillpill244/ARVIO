package com.arflix.tv.util

/**
 * KMP-compatible logging abstraction.
 *
 * On Android the default [PlatformLogger] delegates to `android.util.Log`.
 * On iOS / other targets the actual implementation will use `NSLog` or
 * `platform.posix` – but consuming code never needs to change.
 *
 * Usage:
 *   Logger.d("MyTag", "something happened")
 *   Logger.e("MyTag", "oops", throwable)
 */
object Logger {

    /** Pluggable backend – swap in tests or on other platforms. */
    var backend: LoggerBackend = AndroidLoggerBackend

    fun v(tag: String, msg: String, t: Throwable? = null) = backend.log(LogLevel.VERBOSE, tag, msg, t)
    fun d(tag: String, msg: String, t: Throwable? = null) = backend.log(LogLevel.DEBUG, tag, msg, t)
    fun i(tag: String, msg: String, t: Throwable? = null) = backend.log(LogLevel.INFO, tag, msg, t)
    fun w(tag: String, msg: String, t: Throwable? = null) = backend.log(LogLevel.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = backend.log(LogLevel.ERROR, tag, msg, t)
}

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

interface LoggerBackend {
    fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable? = null)
}

/**
 * Android-specific backend – will live in `androidMain` after KMP split.
 * For now it sits here alongside the interface.
 */
object AndroidLoggerBackend : LoggerBackend {
    override fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> android.util.Log.v(tag, msg, throwable)
            LogLevel.DEBUG   -> android.util.Log.d(tag, msg, throwable)
            LogLevel.INFO    -> android.util.Log.i(tag, msg, throwable)
            LogLevel.WARN    -> android.util.Log.w(tag, msg, throwable)
            LogLevel.ERROR   -> android.util.Log.e(tag, msg, throwable)
        }
    }
}
