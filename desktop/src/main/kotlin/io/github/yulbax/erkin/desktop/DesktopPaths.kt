package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.AppDatabase
import java.io.File

object DesktopPaths {
    val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows")

    val ciadpiUnsupportedOptions: Set<String> = if (isWindows) setOf("-S", "--md5sig") else emptySet()

    val isPortable: Boolean = System.getProperty("erkin.portable").toBoolean()

    val dataDir: File = adoptLegacyData(userDataDir("ERKIN", "erkin"), userDataDir("FRKN", "frkn")).apply { mkdirs() }

    val database: File = File(dataDir, AppDatabase.FILE_NAME)
    val logFile: File = File(dataDir, "erkin.log")
    val languageFile: File = File(dataDir, "language")
    val windowFile: File = File(dataDir, "window")

    val instanceSocket: File = System.getenv("TEMP")?.takeIf { isWindows }?.let { File(it, "erkin-instance.sock") }
        ?: File(dataDir, "instance.sock")

    private val binDir: File = System.getProperty("compose.application.resources.dir")?.let(::File)
        ?: System.getenv("ERKIN_BIN_DIR")?.let(::File)
        ?: File("binaries")

    val serviceSocket: File = System.getenv("ERKIN_SERVICE_SOCKET")?.let(::File)
        ?: if (isWindows) File(System.getenv("ProgramData") ?: "C:\\ProgramData", "ERKIN/erkin.sock")
        else File("/run/erkin/erkin.sock")

    val protectSocket: File = when {
        FlatpakSandbox.isActive -> File(FlatpakSandbox.sharedRuntimeDir(), PROTECT_SOCKET)
        isPortable -> File(System.getenv("TEMP") ?: System.getProperty("user.home"), "erkin-${ProcessHandle.current().pid()}.sock")
        else -> File(serviceSocket.parentFile, PROTECT_SOCKET)
    }

    val serviceBinary: File = File(binDir, executable("erkin-service"))

    val serviceCommand: List<String> =
        if (FlatpakSandbox.isActive) FlatpakSandbox.onHost(serviceBinary) else listOf(serviceBinary.absolutePath)

    val runsServiceItself: Boolean = isPortable || FlatpakSandbox.isActive
    val coreDir: File = File(dataDir, "core")

    val ciadpi: File = File(binDir, executable("ciadpi"))

    val ownExecutable: String? = ProcessHandle.current().info().command().map { File(it).name }.orElse(null)

    val ownApp: OwnApp = OwnApp(ownExecutable, FlatpakSandbox.appId)

    private fun executable(name: String): String = if (isWindows) "$name.exe" else name

    private fun userDataDir(windowsName: String, unixName: String): File =
        System.getenv("APPDATA")?.let { File(it, windowsName) }
            ?: System.getenv("XDG_DATA_HOME")?.takeIf { FlatpakSandbox.isActive }?.let { File(it, unixName) }
            ?: File(System.getProperty("user.home"), ".local/share/$unixName")

    private fun adoptLegacyData(dir: File, legacy: File): File {
        if (dir.exists() || !legacy.isDirectory || !legacy.renameTo(dir)) return dir
        listOf("", "-wal", "-shm").forEach { suffix ->
            File(dir, LEGACY_DATABASE + suffix).renameTo(File(dir, AppDatabase.FILE_NAME + suffix))
        }
        return dir
    }

    private const val LEGACY_DATABASE = "frkn.db"
    private const val PROTECT_SOCKET = "protect.sock"
}

object FlatpakSandbox {
    private const val INFO_FILE = "/.flatpak-info"
    private const val APP_ROOT = "/app"
    private val info = File(INFO_FILE)

    val appId: String? = System.getenv("FLATPAK_ID")?.takeIf { info.isFile }

    val isActive: Boolean = appId != null

    private val hostAppRoot: String by lazy {
        checkNotNull(IniFiles.value(info.readText(), "[Instance]", "app-path")) { "$INFO_FILE has no app-path" }
    }

    fun onHost(file: File): List<String> =
        listOf("flatpak-spawn", "--host", "--watch-bus", hostAppRoot + file.absolutePath.removePrefix(APP_ROOT))

    fun sharedRuntimeDir(): File = File(checkNotNull(System.getenv("XDG_RUNTIME_DIR")), "app/${checkNotNull(appId)}")
}
