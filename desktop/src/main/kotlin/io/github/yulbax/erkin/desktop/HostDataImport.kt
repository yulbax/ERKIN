package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.AppDatabase
import java.io.File

object HostDataImport {
    private val FILES = listOf(
        AppDatabase.FILE_NAME,
        AppDatabase.FILE_NAME + "-wal",
        DesktopDeviceIdentity.FILE_NAME,
        DesktopPaths.LANGUAGE_FILE
    )

    fun into(dir: File, hostDir: File?) {
        if (hostDir == null || File(dir, AppDatabase.FILE_NAME).exists() || !File(hostDir, AppDatabase.FILE_NAME).isFile) return
        runCatching {
            FILES.map { File(hostDir, it) }.filter { it.isFile }.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        }
    }
}
