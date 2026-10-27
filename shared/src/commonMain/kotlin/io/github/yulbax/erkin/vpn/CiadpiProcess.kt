package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CiadpiProcess(
    private val binary: File,
    private val protectPath: String,
    private val log: AppLog,
    private val unsupportedOptions: Set<String> = emptySet()
) : ByeDpiProcess {
    @Volatile private var process: Process? = null

    override val isRunning: Boolean get() = process != null

    @Synchronized
    override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) {
        if (process != null) return
        check(binary.isFile) { "ciadpi binary not found: ${binary.absolutePath}" }
        val supported = args.filterNot { it in unsupportedOptions }
        if (supported.size != args.size) {
            log.w(TAG, "dropping options this ciadpi build does not support: ${args.filter { it in unsupportedOptions }}")
        }
        val started = ProcessBuilder(listOf(binary.absolutePath, "-i", "127.0.0.1", "-p", port.toString(), "-P", protectPath) + supported)
            .redirectErrorStream(true)
            .start()
        process = started
        val output = ArrayDeque<String>()
        val reader = thread(isDaemon = true, name = "ciadpi-output") {
            runCatching {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(output) {
                            output.addLast(line)
                            if (output.size > OUTPUT_LINES) output.removeFirst()
                        }
                    }
                }
            }
        }
        log.i(TAG, "ciadpi started on port $port args=$supported")
        thread(isDaemon = true, name = "ciadpi-exit") {
            val code = runCatching { started.waitFor() }.getOrElse { return@thread }
            if (process !== started) return@thread
            reader.join(OUTPUT_WAIT_MS)
            val tail = synchronized(output) { output.joinToString(" | ") }
            log.w(TAG, "ciadpi exited with code $code" + if (tail.isNotBlank()) ": $tail" else "")
            onUnexpectedExit(code)
        }
    }

    @Synchronized
    override fun stop() {
        val current = process ?: return
        process = null
        current.destroy()
        if (!current.waitFor(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) current.destroyForcibly()
    }

    private companion object {
        const val TAG = "Ciadpi"
        const val STOP_TIMEOUT_MS = 3_000L
        const val OUTPUT_WAIT_MS = 500L
        const val OUTPUT_LINES = 10
    }
}
