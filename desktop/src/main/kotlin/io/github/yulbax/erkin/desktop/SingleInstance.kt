package io.github.yulbax.erkin.desktop

import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

class SingleInstance(dir: File) {
    private val lockFile = File(dir, "instance.lock")
    private val socketFile = File(dir, "instance.sock")
    private var lock: FileLock? = null
    private var server: ServerSocketChannel? = null

    fun claim(onActivate: () -> Unit): Boolean {
        lockFile.parentFile.mkdirs()
        val channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val acquired = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (acquired == null) {
            channel.close()
            activateRunning()
            return false
        }
        lock = acquired
        socketFile.delete()
        val listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
            bind(UnixDomainSocketAddress.of(socketFile.toPath()))
        }
        server = listener
        thread(isDaemon = true, name = "erkin-instance") {
            while (listener.isOpen) {
                val client = runCatching { listener.accept() }.getOrNull() ?: break
                client.use { it.read(ByteBuffer.allocate(1)) }
                onActivate()
            }
        }
        return true
    }

    fun release() {
        runCatching { server?.close() }
        socketFile.delete()
        runCatching { lock?.release() }
        runCatching { lock?.channel()?.close() }
    }

    private fun activateRunning() {
        repeat(CONNECT_ATTEMPTS) {
            try {
                SocketChannel.open(UnixDomainSocketAddress.of(socketFile.toPath())).use {
                    it.write(ByteBuffer.wrap(byteArrayOf(ACTIVATE)))
                }
                return
            } catch (_: IOException) {
                Thread.sleep(CONNECT_RETRY_MS)
            }
        }
    }

    private companion object {
        const val ACTIVATE: Byte = 1
        const val CONNECT_ATTEMPTS = 20
        const val CONNECT_RETRY_MS = 100L
    }
}
