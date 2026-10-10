package io.github.yulbax.erkin.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.yulbax.erkin.BuildConfig
import io.github.yulbax.erkin.update.UpdatePlatform
import java.io.File
import org.koin.core.annotation.Single

@Single(binds = [UpdatePlatform::class])
class AndroidUpdatePlatform(private val context: Context) : UpdatePlatform {
    override val assetSuffix: String?
        get() = runCatching {
            val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            BuildConfig.UPDATE_ABIS.getOrNull((versionCode % ABI_CODES).toInt())
        }.getOrNull()?.let { "android-$it.apk" }

    override val downloadDir: File = File(context.cacheDir, "updates")

    override fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        const val ABI_CODES = 10
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
