package io.github.yulbax.erkin.util

import java.util.Date

object DiagnosticsReport {
    fun build(
        environment: List<Pair<String, String>>,
        log: String,
        profiles: List<String> = emptyList()
    ): String = buildString {
        append("=== ERKIN diagnostics ===\n")
        append("time: ").append(Date()).append('\n')
        environment.forEach { (key, value) -> append(key).append(": ").append(value).append('\n') }
        append("\n=== profiles ===\n")
        if (profiles.isEmpty()) append("(none)\n") else profiles.forEach { append(it).append('\n') }
        append("\n=== log ===\n")
        append(redactSensitiveData(log))
    }
}
