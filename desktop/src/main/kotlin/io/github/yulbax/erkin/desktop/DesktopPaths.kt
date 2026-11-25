package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.AppDatabase
import java.io.File

object DesktopPaths {
    val isPortable: Boolean = System.getProperty("erkin.portable").toBoolean()

    val dataDir: File = adoptLegacyData(desktopSystem.userDataDir("ERKIN", "erkin"), desktopSystem.userDataDir("FRKN", "frkn")).apply { mkdirs() }

    val database: File = File(dataDir, AppDatabase.FILE_NAME)
    val logFile: File = File(dataDir, "erkin.log")
    val languageFile: File = File(dataDir, "language")
    val windowFile: File = File(dataDir, "window")

    val instanceSocket: File = desktopSystem.instanceSocket(dataDir)

    private val binDir: File = System.getProperty("compose.application.resources.dir")?.let(::File)
        ?: System.getenv("ERKIN_BIN_DIR")?.let(::File)
        ?: File("binaries")

    val serviceSocket: File = System.getenv("ERKIN_SERVICE_SOCKET")?.let(::File) ?: desktopSystem.installedServiceSocket

    val serviceBinary: File = File(binDir, desktopSystem.executableName("erkin-service"))

    val coreDir: File = File(dataDir, "core")

    val ciadpi: File = File(binDir, desktopSystem.executableName("ciadpi"))

    val ownExecutable: String? = ProcessHandle.current().info().command().map { File(it).name }.orElse(null)

    val ownApp: OwnApp = OwnApp(ownExecutable, desktopSystem.sandboxApp)

    private fun adoptLegacyData(dir: File, legacy: File): File {
        if (dir.exists() || !legacy.isDirectory || !legacy.renameTo(dir)) return dir
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(dir, LEGACY_DATABASE + suffix).renameTo(File(dir, AppDatabase.FILE_NAME + suffix))
        }
        return dir
    }

    private const val LEGACY_DATABASE = "frkn.db"
}
