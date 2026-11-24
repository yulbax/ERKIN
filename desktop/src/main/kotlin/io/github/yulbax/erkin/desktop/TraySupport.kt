package io.github.yulbax.erkin.desktop

import java.util.concurrent.TimeUnit

object TraySupport {
    val isAvailable: Boolean by lazy {
        if (DesktopPaths.isWindows) true else hasStatusNotifierHost()
    }

    private fun hasStatusNotifierHost(): Boolean = runCatching {
        val process = ProcessBuilder(
            "dbus-send", "--session", "--print-reply", "--dest=org.freedesktop.DBus", "/org/freedesktop/DBus",
            "org.freedesktop.DBus.NameHasOwner", "string:org.kde.StatusNotifierWatcher"
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS) && output.lines().any { it.trim() == "boolean true" }
    }.getOrDefault(false)
}
