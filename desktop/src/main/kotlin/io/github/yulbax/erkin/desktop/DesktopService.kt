package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.core
import java.io.File
import java.util.concurrent.TimeUnit

class DesktopService(
    private val socket: File,
    private val log: AppLog
) {
    @Volatile private var client: ServiceClient? = null
    @Volatile private var control: ServiceClient? = null
    @Volatile var hello: ServiceClient.Hello? = null
        private set

    @Synchronized
    fun connect(): ServiceClient = running() ?: startService() ?: error(NOT_RUNNING)

    fun connectAtLaunch() {
        runCatching { connect() }.onFailure { log.w(TAG, "could not start the ERKIN service", it) }
    }

    @Synchronized
    fun control(): ServiceClient {
        control?.takeIf { it.isOpen }?.let { return it }
        connect()
        return open().also { control = it }
    }

    @Synchronized
    fun running(): ServiceClient? {
        client?.takeIf { it.isOpen }?.let { return it }
        return tryConnect()?.also { client = it }
    }

    @Synchronized
    fun peek(): ServiceClient.Hello? = running()?.let { hello }

    @Synchronized
    fun clearCoreLog() {
        if (peek() == null) return
        runCatching { client?.clearLog() }.onFailure { log.w(TAG, "could not clear the core log", it) }
    }

    private fun startService(): ServiceClient? {
        if (!DesktopPaths.isWindows) return null
        log.i(TAG, "starting the ERKIN service")
        val process = ProcessBuilder("sc.exe", "start", SERVICE_NAME).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.waitFor()
        if (exit != 0 && exit != ALREADY_RUNNING) {
            log.w(TAG, "sc start exited with $exit: $output")
            return null
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_WAIT_S)
        while (System.nanoTime() < deadline) {
            tryConnect()?.let { connected -> return connected.also { client = it } }
            Thread.sleep(300)
        }
        return null
    }

    private fun open(): ServiceClient = ServiceClient.connect(socket) { level, message -> log.core(level, message) }

    private fun tryConnect(): ServiceClient? = runCatching {
        open().also { connected ->
            hello = connected.hello()
            runCatching { connected.subscribeLog() }.onFailure { log.w(TAG, "the service does not stream the core log", it) }
        }
    }.getOrNull()

    private companion object {
        const val TAG = "DesktopService"
        const val SERVICE_NAME = "ERKIN"
        const val ALREADY_RUNNING = 1056
        const val START_WAIT_S = 15L
        const val NOT_RUNNING = "The ERKIN background service is not running. Reinstall ERKIN."
    }
}
