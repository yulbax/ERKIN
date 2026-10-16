package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.geoip.GeoIpDatabase
import io.github.yulbax.erkin.geoip.GeoIpStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopGeoIpStore(private val service: DesktopService) : GeoIpStore {

    override fun installedAt(): Long? =
        service.peek()?.workDir?.let { File(it, GeoIpDatabase.FILE_NAME) }?.takeIf { it.isFile }?.lastModified()

    override fun stagingFile(): File = File(DesktopPaths.dataDir, GeoIpDatabase.FILE_NAME + STAGING_SUFFIX)

    override suspend fun install(downloaded: File) = withContext(Dispatchers.IO) {
        service.connect().importGeoIp(downloaded.absolutePath)
    }

    private companion object {
        const val STAGING_SUFFIX = ".download"
    }
}
