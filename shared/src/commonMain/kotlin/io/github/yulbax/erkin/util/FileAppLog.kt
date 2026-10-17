package io.github.yulbax.erkin.util

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel(val symbol: String) {
    INFO("I"),
    WARN("W"),
    ERROR("E");

    companion object {
        fun fromCore(level: String): LogLevel = when (level) {
            "warning" -> WARN
            "error" -> ERROR
            else -> INFO
        }
    }
}

class FileAppLog(
    private val logFile: File,
    private val echo: (LogLevel, String, String) -> Unit = { _, _, _ -> }
) : AppLog, LogFeed {
    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
    private val lock = Any()
    private val recent by lazy { MutableStateFlow(load()) }
    private val views = LogSource.entries.associateWith { SourceLog(it) }

    override val entries: StateFlow<List<String>> get() = recent.asStateFlow()

    override fun i(tag: String, message: String) = write(LogSource.APP, LogLevel.INFO, tag, message, null)

    override fun w(tag: String, message: String, t: Throwable?) = write(LogSource.APP, LogLevel.WARN, tag, message, t)

    override fun e(tag: String, message: String, t: Throwable?) = write(LogSource.APP, LogLevel.ERROR, tag, message, t)

    override fun of(source: LogSource): AppLog = views.getValue(source)

    fun dump(): String = synchronized(lock) {
        if (logFile.exists()) logFile.readText() else "(empty)"
    }

    fun clear() = synchronized(lock) {
        logFile.delete()
        File(logFile.parentFile, logFile.name + ".1").delete()
        recent.value = emptyList()
    }

    private fun write(source: LogSource, level: LogLevel, tag: String, message: String, t: Throwable?) {
        val text = buildString {
            append(redactSensitiveData(message))
            if (t != null) {
                append('\n')
                append(redactSensitiveData(t.stackTraceToString()).trimEnd())
            }
        }
        echo(level, tag.ifEmpty { source.label }, text)
        val label = if (tag.isEmpty()) level.symbol else "${level.symbol}/$tag"
        val entry = "${timeFormat.format(Instant.now())} [${source.label}] $label: $text"
        synchronized(lock) {
            runCatching {
                logFile.parentFile?.mkdirs()
                if (logFile.length() > MAX_BYTES) {
                    val rotated = File(logFile.parentFile, logFile.name + ".1")
                    rotated.delete()
                    logFile.renameTo(rotated)
                }
                logFile.appendText(entry + "\n")
            }
            recent.value = (recent.value + entry).takeLast(MAX_ENTRIES)
        }
    }

    private fun load(): List<String> {
        val lines = runCatching { if (logFile.exists()) logFile.readLines() else emptyList() }.getOrDefault(emptyList())
        val entries = mutableListOf<String>()
        lines.forEach { line ->
            if (ENTRY_START.containsMatchIn(line) || entries.isEmpty()) entries += line else entries[entries.lastIndex] += "\n" + line
        }
        return entries.takeLast(MAX_ENTRIES)
    }

    private inner class SourceLog(private val source: LogSource) : AppLog {
        override fun i(tag: String, message: String) = write(source, LogLevel.INFO, tag, message, null)

        override fun w(tag: String, message: String, t: Throwable?) = write(source, LogLevel.WARN, tag, message, t)

        override fun e(tag: String, message: String, t: Throwable?) = write(source, LogLevel.ERROR, tag, message, t)

        override fun of(source: LogSource): AppLog = this@FileAppLog.of(source)
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024L
        const val MAX_ENTRIES = 2000
        val ENTRY_START = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} ")
    }
}
