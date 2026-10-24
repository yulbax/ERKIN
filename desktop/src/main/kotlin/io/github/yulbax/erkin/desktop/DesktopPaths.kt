package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.AppDatabase
import java.io.File

enum class DesktopOs(val displayName: String) {
    Windows("Windows"), Linux("Linux"), MacOs("macOS");

    companion object {
        val current: DesktopOs = System.getProperty("os.name").orEmpty().let { name ->
            when {
                name.startsWith("Windows") -> Windows
                name.startsWith("Mac") -> MacOs
                else -> Linux
            }
        }
    }
}

object DesktopPaths {
    val os: DesktopOs = DesktopOs.current
    val isWindows: Boolean = os == DesktopOs.Windows

    val dataDir: File = adoptLegacyData(userDataDir("ERKIN", "erkin"), userDataDir("FRKN", "frkn")).apply { mkdirs() }

    val database: File = File(dataDir, AppDatabase.FILE_NAME)
    val logFile: File = File(dataDir, "erkin.log")

    private val binDir: File = System.getProperty("compose.application.resources.dir")?.let(::File)
        ?: System.getenv("ERKIN_BIN_DIR")?.let(::File)
        ?: File("binaries")

    val serviceBinary: File = File(binDir, executable("erkin-service"))

    val serviceSocket: File = System.getenv("ERKIN_SERVICE_SOCKET")?.let(::File)
        ?: when (os) {
            DesktopOs.Windows -> File(System.getenv("ProgramData") ?: "C:\\ProgramData", "ERKIN Service/erkin.sock")
            DesktopOs.Linux -> File("/run/erkin/erkin.sock")
            DesktopOs.MacOs -> File("/var/run/erkin/erkin.sock")
        }

    val protectSocket: File = File(serviceSocket.parentFile, "protect.sock")

    val installDir: File? = System.getProperty("compose.application.resources.dir")?.let {
        ProcessHandle.current().info().command().map { command -> File(command).parentFile }.orElse(null)
    }
    val ciadpi: File = File(binDir, executable("ciadpi"))

    val ownExecutable: String? = ProcessHandle.current().info().command().map { File(it).name }.orElse(null)

    private fun executable(name: String): String = if (isWindows) "$name.exe" else name

    private fun userDataDir(windowsName: String, unixName: String): File {
        val home = System.getProperty("user.home")
        return when (os) {
            DesktopOs.Windows -> File(System.getenv("APPDATA") ?: home, windowsName)
            DesktopOs.Linux -> File(home, ".local/share/$unixName")
            DesktopOs.MacOs -> File(home, "Library/Application Support/$windowsName")
        }
    }

    private fun adoptLegacyData(dir: File, legacy: File): File {
        if (dir.exists() || !legacy.isDirectory || !legacy.renameTo(dir)) return dir
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(dir, LEGACY_DATABASE + suffix).renameTo(File(dir, AppDatabase.FILE_NAME + suffix))
        }
        return dir
    }

    private const val LEGACY_DATABASE = "frkn.db"
}
