package io.github.yulbax.erkin.util

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LogLevel(val symbol: String) { INFO("I"), WARN("W"), ERROR("E") }

class FileAppLog(
    private val logFile: File,
    private val echo: (LogLevel, String, String) -> Unit = { _, _, _ -> }
) : AppLog {
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val lock = Any()

    override fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)

    override fun w(tag: String, message: String, t: Throwable?) = log(LogLevel.WARN, tag, message, t)

    override fun e(tag: String, message: String, t: Throwable?) = log(LogLevel.ERROR, tag, message, t)

    fun dump(): String = synchronized(lock) {
        if (logFile.exists()) logFile.readText() else "(empty)"
    }

    fun clear() = synchronized(lock) {
        logFile.delete()
        File(logFile.parentFile, logFile.name + ".1").delete()
    }

    private fun log(level: LogLevel, tag: String, message: String, t: Throwable?) {
        val text = buildString {
            append(redactSensitiveData(message))
            if (t != null) {
                append('\n')
                append(redactSensitiveData(t.stackTraceToString()).trimEnd())
            }
        }
        echo(level, tag, text)
        val line = "${timeFormat.format(Instant.now())} ${level.symbol}/$tag: $text\n"
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                if (logFile.length() > MAX_BYTES) {
                    val rotated = File(logFile.parentFile, logFile.name + ".1")
                    rotated.delete()
                    logFile.renameTo(rotated)
                }
                logFile.appendText(line)
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024L
    }
}
