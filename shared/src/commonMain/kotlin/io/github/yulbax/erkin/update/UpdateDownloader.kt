package io.github.yulbax.erkin.update

import java.io.File
import java.io.IOException
import io.github.yulbax.erkin.vpn.DownloadRoute
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UpdateDownloader(private val route: DownloadRoute = DownloadRoute()) {
    suspend fun download(asset: ReleaseAsset, dir: File, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            dir.mkdirs()
            val target = File(dir, asset.name)
            val partial = File(dir, asset.name + PARTIAL_SUFFIX)
            val context = coroutineContext
            val actual = route.fetch(asset.url) { connection ->
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size.takeIf { it > 0 }
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var done = 0L
                            while (true) {
                                context.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                done += read
                                onProgress(total?.let { (done.toFloat() / it).coerceIn(0f, 1f) })
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            if (asset.sha256 != null && !asset.sha256.equals(actual, ignoreCase = true)) {
                partial.delete()
                throw IOException("Checksum mismatch for ${asset.name}")
            }
            if (!partial.renameTo(target)) throw IOException("Could not save ${asset.name}")
            target
        }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val PARTIAL_SUFFIX = ".part"
    }
}
