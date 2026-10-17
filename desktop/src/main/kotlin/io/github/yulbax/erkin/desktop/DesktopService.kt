package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.core
import java.io.File
import java.util.concurrent.TimeUnit

class DesktopService(
    private val socket: File,
    private val bundled: File,
    private val appDir: File?,
    private val expectedVersion: String?,
    private val log: AppLog
) {
    @Volatile private var client: ServiceClient? = null
    @Volatile var hello: ServiceClient.Hello? = null
        private set

    @Synchronized
    fun connect(): ServiceClient {
        client?.takeIf { it.isOpen }?.let { return it }
        var connected = tryConnect()
        if (connected == null || isOutdated(connected)) {
            connected?.close()
            install()
            connected = awaitConnection() ?: error(NOT_RUNNING)
        }
        client = connected
        return connected
    }

    @Synchronized
    fun peek(): ServiceClient.Hello? {
        client?.takeIf { it.isOpen }?.let { return hello }
        val connected = tryConnect() ?: return null
        client = connected
        return hello
    }

    @Synchronized
    fun clearCoreLog() {
        if (peek() == null) return
        runCatching { client?.clearLog() }.onFailure { log.w(TAG, "could not clear the core log", it) }
    }

    private fun tryConnect(): ServiceClient? = runCatching {
        ServiceClient.connect(socket) { level, message -> log.core(level, message) }.also { connected ->
            hello = connected.hello()
            runCatching { connected.subscribeLog() }.onFailure { log.w(TAG, "the service does not stream the core log", it) }
        }
    }.getOrNull()

    private fun isOutdated(connected: ServiceClient): Boolean {
        val running = hello?.version ?: return true
        val outdated = expectedVersion != null && running != expectedVersion && bundled.isFile
        if (outdated) log.i(TAG, "service $running is outdated, expected $expectedVersion")
        return outdated
    }

    private fun install() {
        check(bundled.isFile) { "ERKIN service not found: ${bundled.absolutePath}" }
        log.i(TAG, "installing the ERKIN service from $bundled")
        val args = buildList {
            add("install")
            appDir?.let { add("--app-dir"); add(it.absolutePath) }
        }
        val command = if (DesktopPaths.isWindows) {
            val argumentList = args.joinToString(",") { "'" + quoteForPowerShell(it) + "'" }
            listOf(
                "powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                "\$p = Start-Process -FilePath '${quoteForPowerShell(bundled.absolutePath)}' " +
                    "-ArgumentList $argumentList -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit \$p.ExitCode"
            )
        } else {
            listOf("pkexec", bundled.absolutePath) + args
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!process.waitFor(INSTALL_TIMEOUT_S, TimeUnit.SECONDS)) {
            process.destroy()
            error(INSTALL_FAILED)
        }
        if (process.exitValue() != 0) {
            log.w(TAG, "service install exited with ${process.exitValue()}: $output")
            error(INSTALL_FAILED)
        }
    }

    private fun awaitConnection(): ServiceClient? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CONNECT_WAIT_S)
        while (System.nanoTime() < deadline) {
            tryConnect()?.let { return it }
            Thread.sleep(300)
        }
        return null
    }

    private fun quoteForPowerShell(value: String): String = value.replace("'", "''").replace("\"", "\\\"")

    private companion object {
        const val TAG = "DesktopService"
        const val INSTALL_TIMEOUT_S = 300L
        const val CONNECT_WAIT_S = 15L
        const val INSTALL_FAILED = "The ERKIN background service could not be installed. It needs administrator rights once."
        const val NOT_RUNNING = "The ERKIN background service is not running."
    }
}
