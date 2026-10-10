package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.AppDatabase
import java.io.File

object DesktopPaths {
    val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows")

    val dataDir: File = adoptLegacyData(userDataDir("ERKIN", "erkin"), userDataDir("FRKN", "frkn")).apply { mkdirs() }

    val database: File = File(dataDir, AppDatabase.FILE_NAME)
    val logFile: File = File(dataDir, "erkin.log")

    private val binDir: File = System.getProperty("compose.application.resources.dir")?.let(::File)
        ?: System.getenv("ERKIN_BIN_DIR")?.let(::File)
        ?: File("binaries")

    val serviceBinary: File = File(binDir, executable("erkin-service"))

    val serviceSocket: File = System.getenv("ERKIN_SERVICE_SOCKET")?.let(::File)
        ?: if (isWindows) File(System.getenv("ProgramData") ?: "C:\\ProgramData", "ERKIN Service/erkin.sock")
        else File("/run/erkin/erkin.sock")

    val installDir: File? = System.getProperty("compose.application.resources.dir")?.let {
        ProcessHandle.current().info().command().map { command -> File(command).parentFile }.orElse(null)
    }
    val ciadpi: File = File(binDir, executable("ciadpi"))

    val ownExecutable: String? = ProcessHandle.current().info().command().map { File(it).name }.orElse(null)

    private fun executable(name: String): String = if (isWindows) "$name.exe" else name

    private fun userDataDir(windowsName: String, unixName: String): File =
        System.getenv("APPDATA")?.let { File(it, windowsName) }
            ?: File(System.getProperty("user.home"), ".local/share/$unixName")

    private fun adoptLegacyData(dir: File, legacy: File): File {
        if (dir.exists() || !legacy.isDirectory || !legacy.renameTo(dir)) return dir
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(dir, LEGACY_DATABASE + suffix).renameTo(File(dir, AppDatabase.FILE_NAME + suffix))
        }
        return dir
    }

    private const val LEGACY_DATABASE = "frkn.db"
}
