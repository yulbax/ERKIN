package io.github.yulbax.erkin.util

import java.io.File
import java.util.Date

object DiagnosticsReport {
    private const val MAX_CORE_LOG_BYTES = 1024 * 1024L

    fun build(
        environment: List<Pair<String, String>>,
        appLog: String,
        coreLog: File,
        profiles: List<String> = emptyList()
    ): String = buildString {
        append("=== ERKIN diagnostics ===\n")
        append("time: ").append(Date()).append('\n')
        environment.forEach { (key, value) -> append(key).append(": ").append(value).append('\n') }
        append("\n=== profiles ===\n")
        if (profiles.isEmpty()) append("(none)\n") else profiles.forEach { append(it).append('\n') }
        append("\n=== app log (erkin.log) ===\n")
        append(redactSensitiveData(appLog))
        append("\n=== mihomo (${coreLog.name}) ===\n")
        append(
            when {
                !coreLog.exists() -> "(no ${coreLog.name})"
                else -> runCatching { redactSensitiveData(tail(coreLog, MAX_CORE_LOG_BYTES)) }
                    .getOrElse { "(cannot read ${coreLog.name}: ${it.message})" }
            }
        )
    }

    private fun tail(file: File, maxBytes: Long): String {
        val length = file.length()
        if (length <= maxBytes) return file.readText()
        return file.inputStream().use { stream ->
            stream.skip(length - maxBytes)
            "(truncated, showing last ${maxBytes / 1024} KB of ${length / 1024} KB)\n" +
                stream.readBytes().decodeToString()
        }
    }
}
