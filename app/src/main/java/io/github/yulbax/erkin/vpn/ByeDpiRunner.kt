package io.github.yulbax.erkin.vpn

import android.os.SystemClock
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import java.io.File

const val BYEDPI_VERSION = "17.3"

class ByeDpiRunner(
    private val packageName: String,
    private val binary: File,
    private val log: AppLog,
    private val protect: (Int) -> Boolean
) : ByeDpiProcess {
    private var process: CiadpiProcess? = null
    private var protector: SocketProtector? = null

    override val isRunning: Boolean get() = process != null

    override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) {
        if (process != null) return
        val protectName = "$packageName.byedpi-protect.${SystemClock.elapsedRealtimeNanos()}"
        val newProtector = SocketProtector(protectName, log, protect)
        val newProcess = CiadpiProcess(binary, protectName, log)
        try {
            newProtector.start()
            protector = newProtector
            process = newProcess
            newProcess.start(port, args, onUnexpectedExit)
        } catch (t: Throwable) {
            process = null
            protector = null
            runCatching { newProcess.stop() }
            runCatching { newProtector.stop() }
            throw t
        }
    }

    override fun stop() {
        val currentProcess = process
        val currentProtector = protector
        process = null
        protector = null
        var failure: Throwable? = null
        try {
            currentProcess?.stop()
        } catch (t: Throwable) {
            failure = t
        }
        try {
            currentProtector?.stop()
        } catch (t: Throwable) {
            if (failure == null) failure = t else failure.addSuppressed(t)
        }
        failure?.let { throw it }
    }

    companion object {
        const val BINARY_NAME = "libciadpi.so"
    }
}
