package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.core
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

sealed class DesktopService(protected val log: AppLog) {
    @Volatile var hello: ServiceClient.Hello? = null
        private set

    abstract fun connect(): ServiceClient

    abstract fun control(): ServiceClient

    abstract fun running(): ServiceClient?

    fun connectAtLaunch() {
        runCatching { connect() }.onFailure { log.w(TAG, "could not start the ERKIN core", it) }
    }

    @Synchronized
    fun peek(): ServiceClient.Hello? = running()?.let { hello }

    @Synchronized
    fun clearCoreLog() {
        val client = running() ?: return
        runCatching { client.clearLog() }.onFailure { log.w(TAG, "could not clear the core log", it) }
    }

    protected fun greet(client: ServiceClient): ServiceClient {
        try {
            hello = client.hello()
        } catch (t: Throwable) {
            client.close()
            throw t
        }
        runCatching { client.subscribeLog() }.onFailure { log.w(TAG, "the core does not stream its log", it) }
        return client
    }

    protected fun forwardLog(level: String, message: String) = log.core(level, message)

    class Installed(private val socket: File, log: AppLog) : DesktopService(log) {
        @Volatile private var client: ServiceClient? = null
        @Volatile private var control: ServiceClient? = null

        @Synchronized
        override fun connect(): ServiceClient = running() ?: startService() ?: error(NOT_RUNNING)

        @Synchronized
        override fun control(): ServiceClient {
            control?.takeIf { it.isOpen }?.let { return it }
            connect()
            return open().also { control = it }
        }

        @Synchronized
        override fun running(): ServiceClient? {
            client?.takeIf { it.isOpen }?.let { return it }
            return tryConnect()?.also { client = it }
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

        private fun open(): ServiceClient = ServiceClient.connect(socket, ::forwardLog)

        private fun tryConnect(): ServiceClient? = runCatching { greet(open()) }.getOrNull()
    }

    class Portable(
        private val binary: File,
        private val workDir: File,
        private val protectSocket: File,
        private val installedSocket: File,
        log: AppLog
    ) : DesktopService(log) {
        @Volatile private var client: ServiceClient? = null

        @Synchronized
        override fun connect(): ServiceClient = running() ?: launch()

        override fun control(): ServiceClient = connect()

        @Synchronized
        override fun running(): ServiceClient? = client?.takeIf { it.isOpen }

        private fun launch(): ServiceClient {
            check(!installedServiceRuns()) { INSTALLED_RUNNING }
            log.i(TAG, "starting the ERKIN core")
            val process = ProcessBuilder(
                binary.absolutePath, "run", "--stdio",
                "--work-dir", workDir.absolutePath,
                "--protect", protectSocket.absolutePath
            ).start()
            thread(isDaemon = true, name = "erkin-core-errors") {
                process.errorStream.bufferedReader().useLines { lines -> lines.forEach { log.e(TAG, "core: $it") } }
            }
            process.onExit().thenAccept { log.e(TAG, "the ERKIN core exited with code ${it.exitValue()}") }
            return try {
                greet(ServiceClient.attach(process, ::forwardLog)).also { client = it }
            } catch (t: Throwable) {
                process.destroy()
                throw t
            }
        }

        private fun installedServiceRuns(): Boolean = runCatching { ServiceClient.connect(installedSocket).close() }.isSuccess
    }

    private companion object {
        const val TAG = "DesktopService"
        const val SERVICE_NAME = "ERKIN"
        const val ALREADY_RUNNING = 1056
        const val START_WAIT_S = 15L
        const val NOT_RUNNING = "The ERKIN background service is not running. Reinstall ERKIN."
        const val INSTALLED_RUNNING = "The installed ERKIN is running. Close it before connecting with the portable one."
    }
}
