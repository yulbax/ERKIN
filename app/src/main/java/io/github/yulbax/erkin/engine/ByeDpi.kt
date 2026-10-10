package io.github.yulbax.erkin.engine

import io.github.yulbax.erkin.util.ErkinLog
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


const val BYEDPI_VERSION = "17.3"

class ByeDpi(
    private val log: ErkinLog,
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val protectPath: String? = null,
    private val extraArgs: List<String> = ByeDpiArgs.DEFAULT,
    private val onUnexpectedExit: ((Int) -> Unit)? = null
) {
    private val running = AtomicBoolean(false)
    @Volatile private var stopRequested = false
    private var worker: Thread? = null

    fun start() {
        val args = buildList {
            add("ciadpi")
            add("-i"); add(host)
            add("-p"); add(port.toString())
            if (protectPath != null) { add("-P"); add(protectPath) }
            addAll(extraArgs)
        }.toTypedArray()

        synchronized(LIFECYCLE_LOCK) {
            if (running.get()) return
            check(activeInstance == null) { "another ByeDPI worker is still active" }
            check(nativePrepareStart()) { "native ByeDPI worker is still active" }
            stopRequested = false
            running.set(true)
            activeInstance = this
            val newWorker = thread(start = false, name = "byedpi") {
                val code = try {
                    nativeStart(args)
                } catch (t: Throwable) {
                    log.e(TAG, "byedpi native worker failed", t)
                    -1
                }
                log.i(TAG, "byedpi exited with code $code")
                val unexpected = synchronized(LIFECYCLE_LOCK) {
                    running.set(false)
                    worker = null
                    if (activeInstance === this) activeInstance = null
                    !stopRequested
                }
                if (unexpected) onUnexpectedExit?.invoke(code)
            }
            worker = newWorker
            try {
                newWorker.start()
            } catch (t: Throwable) {
                runCatching { nativeStop() }
                worker = null
                activeInstance = null
                running.set(false)
                throw t
            }
        }
    }

    fun stop() {
        val activeWorker = synchronized(LIFECYCLE_LOCK) {
            val current = worker ?: return
            stopRequested = true
            current
        }
        nativeStop()
        if (activeWorker !== Thread.currentThread()) activeWorker.join()
        synchronized(LIFECYCLE_LOCK) {
            check(!activeWorker.isAlive) { "ByeDPI worker did not stop" }
            if (worker === activeWorker) worker = null
            if (activeInstance === this) activeInstance = null
            running.set(false)
        }
    }

    fun isRunning(): Boolean = running.get()

    private external fun nativeStart(args: Array<String>): Int
    private external fun nativePrepareStart(): Boolean
    private external fun nativeStop()

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 1081
        private const val TAG = "ByeDpi"
        private val LIFECYCLE_LOCK = Any()
        private var activeInstance: ByeDpi? = null

        init {
            System.loadLibrary("byedpi")
        }
    }
}
