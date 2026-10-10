package io.github.yulbax.erkin.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ShareFiles {
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1_000

    suspend fun write(context: Context, prefix: String, extension: String, content: String): Uri =
        withContext(Dispatchers.IO) {
            val file = freshFile(context, prefix, extension)
            file.writeText(content)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    fun intent(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun freshFile(context: Context, prefix: String, extension: String): File {
        val directory = File(context.cacheDir, "share").apply { mkdirs() }
        val staleBefore = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (
                file.isFile &&
                file.name.startsWith("$prefix-") &&
                file.name.endsWith(".$extension") &&
                file.lastModified() < staleBefore
            ) {
                file.delete()
            }
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        return File(directory, "$prefix-$timestamp-$uniqueSuffix.$extension")
    }
}
