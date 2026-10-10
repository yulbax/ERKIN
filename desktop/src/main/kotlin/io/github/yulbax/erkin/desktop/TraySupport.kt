package io.github.yulbax.erkin.desktop

import java.util.concurrent.TimeUnit

object TraySupport {
    val isAvailable: Boolean by lazy {
        if (DesktopPaths.isWindows) true else hasStatusNotifierHost()
    }

    private fun hasStatusNotifierHost(): Boolean = runCatching {
        val process = ProcessBuilder(
            "busctl", "--user", "call", "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
            "NameHasOwner", "s", "org.kde.StatusNotifierWatcher"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS) && output.trim() == "b true"
    }.getOrDefault(false)
}
