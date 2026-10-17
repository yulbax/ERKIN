package io.github.yulbax.erkin.util

import kotlinx.coroutines.flow.StateFlow

interface AppLog {
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, t: Throwable? = null)
    fun e(tag: String, message: String, t: Throwable? = null)

    fun of(source: LogSource): AppLog = this
}

enum class LogSource(val label: String) {
    APP("app"),
    CORE("core"),
    BYEDPI("byedpi")
}

interface LogFeed {
    val entries: StateFlow<List<String>>
}

fun AppLog.core(level: String, message: String) {
    val core = of(LogSource.CORE)
    when (LogLevel.fromCore(level)) {
        LogLevel.INFO -> core.i("", message)
        LogLevel.WARN -> core.w("", message)
        LogLevel.ERROR -> core.e("", message)
    }
}
