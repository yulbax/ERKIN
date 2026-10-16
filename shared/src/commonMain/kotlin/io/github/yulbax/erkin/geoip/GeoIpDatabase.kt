package io.github.yulbax.erkin.geoip

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface GeoIpStore {
    fun installedAt(): Long?

    fun stagingFile(): File

    suspend fun install(downloaded: File)
}

sealed interface GeoIpState {
    data object Missing : GeoIpState
    data class Ready(val updatedAt: Long) : GeoIpState
    data class Downloading(val progress: Float?) : GeoIpState
}

class GeoIpDatabase(private val store: GeoIpStore) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<GeoIpState>(GeoIpState.Missing)
    val state: StateFlow<GeoIpState> = _state.asStateFlow()

    val isReady: Boolean get() = store.installedAt() != null

    fun refresh() {
        if (_state.value !is GeoIpState.Downloading) _state.value = currentState()
    }

    suspend fun download(): Result<Unit> = mutex.withLock {
        _state.value = GeoIpState.Downloading(null)
        try {
            val file = withContext(Dispatchers.IO) { fetch(store.stagingFile()) }
            store.install(file)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            store.stagingFile().delete()
            _state.value = currentState()
        }
    }

    private fun currentState(): GeoIpState = store.installedAt()?.let { GeoIpState.Ready(it) } ?: GeoIpState.Missing

    private suspend fun fetch(target: File): File {
        target.parentFile?.mkdirs()
        val connection = URI(URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_BYTES) throw IOException("GeoIP database is too large")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > MAX_BYTES) throw IOException("GeoIP database is too large")
                        output.write(buffer, 0, read)
                        _state.value = GeoIpState.Downloading(total?.let { (done.toFloat() / it).coerceIn(0f, 1f) })
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!looksLikeMaxMindDb(target)) throw IOException("Downloaded file is not a GeoIP database")
        return target
    }

    companion object {
        const val FILE_NAME = "geoip.metadb"
        const val URL = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb"
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_BYTES = 64L shl 20
        private const val METADATA_WINDOW = 128 * 1024
        private val METADATA_MARKER = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte()) + "MaxMind.com".toByteArray()

        fun looksLikeMaxMindDb(file: File): Boolean {
            val length = file.length()
            if (length < METADATA_MARKER.size) return false
            val window = minOf(length, METADATA_WINDOW.toLong()).toInt()
            val tail = ByteArray(window)
            RandomAccessFile(file, "r").use { it.seek(length - window); it.readFully(tail) }
            return (0..tail.size - METADATA_MARKER.size).any { start ->
                METADATA_MARKER.indices.all { tail[start + it] == METADATA_MARKER[it] }
            }
        }
    }
}
