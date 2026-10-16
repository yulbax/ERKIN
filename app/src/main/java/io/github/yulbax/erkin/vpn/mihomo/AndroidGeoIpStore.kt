package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.geoip.GeoIpDatabase
import io.github.yulbax.erkin.geoip.GeoIpStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihomocore.Mihomocore

class AndroidGeoIpStore(private val context: Context) : GeoIpStore {
    private val target: File get() = File(MihomoRuntime.workDir(context), GeoIpDatabase.FILE_NAME)

    override fun installedAt(): Long? = target.takeIf { it.isFile }?.lastModified()

    override fun stagingFile(): File = File(MihomoRuntime.workDir(context), GeoIpDatabase.FILE_NAME + STAGING_SUFFIX)

    override suspend fun install(downloaded: File) = withContext(Dispatchers.IO) {
        if (!downloaded.renameTo(target)) throw IOException("Could not save the GeoIP database")
        Mihomocore.reloadGeoIP()
    }

    private companion object {
        const val STAGING_SUFFIX = ".download"
    }
}
