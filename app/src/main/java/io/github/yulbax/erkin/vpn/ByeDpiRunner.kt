package io.github.yulbax.erkin.vpn

import android.os.SystemClock
import io.github.yulbax.erkin.engine.ByeDpi
import io.github.yulbax.erkin.util.ErkinLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess

class ByeDpiRunner(
    private val packageName: String,
    private val log: ErkinLog,
    private val protect: (Int) -> Boolean
) : ByeDpiProcess {
    private var process: ByeDpi? = null
    private var protector: SocketProtector? = null

    override val isRunning: Boolean get() = process != null

    override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) {
        if (process != null) return
        val protectName = "$packageName.byedpi-protect.${SystemClock.elapsedRealtimeNanos()}"
        val newProtector = SocketProtector(protectName, log, protect)
        val newProcess = ByeDpi(
            log = log,
            port = port,
            protectPath = protectName,
            extraArgs = args,
            onUnexpectedExit = onUnexpectedExit
        )
        try {
            newProtector.start()
            protector = newProtector
            process = newProcess
            newProcess.start()
            log.i(TAG, "byedpi started on port $port args=$args")
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

    private companion object {
        const val TAG = "ByeDpiRunner"
    }
}
