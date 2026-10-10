package io.github.yulbax.erkin.vpn.core

import java.io.File

class FingerprintErrorWatch(private val boxLog: () -> File?) {
    @Volatile private var scanFrom = 0L

    fun markCoreStart() {
        scanFrom = boxLog()?.length() ?: 0L
    }

    fun hasError(): Boolean = runCatching {
        val log = boxLog()?.takeIf { it.exists() } ?: return false
        val from = if (log.length() < scanFrom) 0L else scanFrom
        log.inputStream().use { input ->
            input.channel.position(from)
            input.bufferedReader().useLines { lines -> lines.any { it.contains(MARKER, ignoreCase = true) } }
        }
    }.getOrDefault(false)

    private companion object {
        const val MARKER = "unsupported curve"
    }
}
