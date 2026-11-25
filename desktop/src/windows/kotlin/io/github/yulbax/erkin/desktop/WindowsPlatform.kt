package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.mihomo.DesktopOs
import io.github.yulbax.erkin.vpn.mihomo.SystemDns
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.File

object WindowsSystem : DesktopSystem {
    override val desktopOs: DesktopOs = DesktopOs.WINDOWS
    override val deviceOsName: String = "Windows"
    override val newAppsChannel: ConnectionType = ConnectionType.DIRECT
    override val ciadpiUnsupportedOptions: Set<String> = setOf("-S", "--md5sig")
    override val installedServiceSocket: File = File(System.getenv("ProgramData") ?: "C:\\ProgramData", "ERKIN/erkin.sock")
    override val sandboxApp: String? = null

    override fun executableName(base: String): String = "$base.exe"

    override fun instanceSocket(dataDir: File): File =
        System.getenv("TEMP")?.let { File(it, "erkin-instance.sock") } ?: File(dataDir, "instance.sock")

    override fun userDataDir(windowsName: String, unixName: String): File =
        System.getenv("APPDATA")?.let { File(it, windowsName) }
            ?: File(System.getProperty("user.home"), ".local/share/$unixName")

    override fun hostDataDir(unixName: String): File? = null
}

object WindowsServiceHost : ServiceHost {
    private const val TAG = "DesktopService"
    private const val SERVICE_NAME = "ERKIN"
    private const val ALREADY_RUNNING = 1056

    override val sandboxProtectSocket: File? = null

    override val serviceCommand: List<String> = listOf(DesktopPaths.serviceBinary.absolutePath)

    override fun startInstalled(log: AppLog): Boolean {
        log.i(TAG, "starting the ERKIN service")
        val process = ProcessBuilder("sc.exe", "start", SERVICE_NAME).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exit = process.waitFor()
        if (exit != 0 && exit != ALREADY_RUNNING) {
            log.w(TAG, "sc start exited with $exit: $output")
            return false
        }
        return true
    }
}

object WindowsPrograms : DesktopPrograms {
    override fun catalog(): List<CatalogProgram> = WindowsStartApps.list()

    override fun running(hostProcesses: () -> List<HostProcess>): List<RunningProcess> {
        val windowed = WindowsWindows.withWindows()
        val systemRoot = System.getenv("SystemRoot")
        return ProcessHandle.allProcesses().toList().mapNotNull { process ->
            process.info().command().orElse(null)?.let { command ->
                val executable = ProcessInstalledApps.executableOf(command)
                RunningProcess(
                    executable = executable,
                    isProgram = process.pid() in windowed && systemRoot?.let { executable.path.startsWith(it, ignoreCase = true) } != true
                )
            }
        }
    }

    override fun stableId(path: String): String = WindowsAppIds.stable(path)

    override fun isStableId(id: String): Boolean = WindowsAppIds.isStable(id)
}

object WindowsTrayHost : TrayHost {
    override val isAvailable: Boolean = true
}

object WindowsWindowChrome : WindowChrome {
    override fun roundCorners(window: Window) = WindowsDwm.roundCorners(window)

    override fun limitWidth(window: Window, maxWidth: Int) {
        window.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (window.width > maxWidth) window.setSize(maxWidth, window.height)
            }
        })
        if (window.width > maxWidth) window.setSize(maxWidth, window.height)
    }
}

object LocalSystemDns : SystemDnsSource {
    override fun detect(): SystemDns = SystemDns.Local
}

object WindowsFileShell : FileShell {
    override val executableIcon: ((path: String, size: Int) -> BufferedImage?)? = WindowsShell::icon

    override val revealInFolder: ((path: String) -> Unit)? = { path -> if (File(path).isFile) WindowsShell.revealInExplorer(path) }
}
