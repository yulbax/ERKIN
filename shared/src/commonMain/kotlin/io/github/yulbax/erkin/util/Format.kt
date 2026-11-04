package io.github.yulbax.erkin.util

import java.util.Locale

fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000 -> "%.0f KB/s".format(bytesPerSec / 1_000.0)
    else -> "$bytesPerSec B/s"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), if (value < 10) "%.1f %s" else "%.0f %s", value, BYTE_UNITS[unit])
}

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")
