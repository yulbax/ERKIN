package io.github.yulbax.erkin.util

fun formatRate(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000 -> "%.0f KB/s".format(bytesPerSec / 1_000.0)
    else -> "$bytesPerSec B/s"
}
