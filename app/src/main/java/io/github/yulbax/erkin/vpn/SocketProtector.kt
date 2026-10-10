package io.github.yulbax.erkin.vpn

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import android.os.ParcelFileDescriptor
import io.github.yulbax.erkin.util.ErkinLog
import java.io.FileDescriptor
import kotlin.concurrent.thread

class SocketProtector(
    val name: String,
    private val log: ErkinLog,
    private val protect: (Int) -> Boolean
) {
    private var serverSocket: LocalServerSocket? = null
    private var worker: Thread? = null
    @Volatile private var activeSocket: LocalSocket? = null
    @Volatile private var running = false
    @Volatile private var loggedFailure = false

    @Synchronized
    fun start() {
        if (running) return
        val server = LocalServerSocket(name)
        serverSocket = server
        running = true
        worker = thread(name = "erkin-protect") {
            while (running) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                if (!running) {
                    runCatching { socket.close() }
                    break
                }
                activeSocket = socket
                try {
                    handle(socket)
                } finally {
                    if (activeSocket === socket) activeSocket = null
                }
            }
        }
    }

    private fun handle(socket: LocalSocket) {
        var fds: Array<FileDescriptor>? = null
        try {
            socket.soTimeout = IO_TIMEOUT_MS
            check(socket.peerCredentials.uid == Process.myUid()) { "unexpected peer uid" }
            check(socket.inputStream.read() >= 0) { "protect request ended before fd transfer" }
            val receivedFds = socket.ancillaryFileDescriptors
            fds = receivedFds
            check(!receivedFds.isNullOrEmpty()) { "no ancillary fds received" }
            val ok = ParcelFileDescriptor.dup(receivedFds[0]).use { duplicate ->
                protect(duplicate.fd)
            }
            if (!ok) logFailureOnce("VpnService.protect rejected fd", null)
            socket.outputStream.write(if (ok) 0 else 1)
            socket.outputStream.flush()
        } catch (e: Exception) {
            if (running) logFailureOnce("protect handling failed", e)
        } finally {
            fds?.forEach { runCatching { closeFd(it) } }
            runCatching { socket.close() }
        }
    }

    private fun logFailureOnce(message: String, t: Throwable?) {
        if (loggedFailure) return
        loggedFailure = true
        log.w(TAG, "$message (further occurrences suppressed)", t)
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { activeSocket?.close() }
        unblockAccept()
        runCatching { serverSocket?.close() }
        val thread = worker
        runCatching { thread?.join(STOP_TIMEOUT_MS) }
        check(thread?.isAlive != true) { "socket protector worker did not stop" }
        activeSocket = null
        serverSocket = null
        worker = null
    }

    private fun unblockAccept() {
        if (worker?.isAlive != true) return
        runCatching {
            LocalSocket().use {
                it.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
            }
        }
    }

    private fun closeFd(fd: FileDescriptor) {
        android.system.Os.close(fd)
    }

    companion object {
        private const val TAG = "SocketProtector"
        private const val IO_TIMEOUT_MS = 5_000
        private const val STOP_TIMEOUT_MS = 2_000L
    }
}
