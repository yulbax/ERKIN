package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.mihomo.DesktopOs
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File

object LinuxSystem : DesktopSystem {
    override val desktopOs: DesktopOs = DesktopOs.LINUX
    override val deviceOsName: String = "Linux"
    override val newAppsChannel: ConnectionType = ConnectionType.VPN
    override val ciadpiUnsupportedOptions: Set<String> = emptySet()
    override val installedServiceSocket: File = File("/run/erkin/erkin.sock")
    override val sandboxApp: String? = FlatpakSandbox.appId

    override fun executableName(base: String): String = base

    override fun instanceSocket(dataDir: File): File = File(dataDir, "instance.sock")

    override fun userDataDir(windowsName: String, unixName: String): File =
        System.getenv("XDG_DATA_HOME")?.takeIf { FlatpakSandbox.isActive }?.let { File(it, unixName) }
            ?: File(System.getProperty("user.home"), ".local/share/$unixName")

    override fun hostDataDir(unixName: String): File? =
        File(System.getProperty("user.home"), ".local/share/$unixName").takeIf { FlatpakSandbox.isActive }
}

object LinuxServiceHost : ServiceHost {
    override val sandboxProtectSocket: File? =
        if (FlatpakSandbox.isActive) File(FlatpakSandbox.sharedRuntimeDir(), PROTECT_SOCKET) else null

    override val serviceCommand: List<String> =
        if (FlatpakSandbox.isActive) FlatpakSandbox.onHost(DesktopPaths.serviceBinary) else listOf(DesktopPaths.serviceBinary.absolutePath)

    override fun startInstalled(log: AppLog): Boolean = false
}

object LinuxPrograms : DesktopPrograms {
    override fun catalog(): List<CatalogProgram> = LinuxApplications.list()

    override fun running(hostProcesses: () -> List<HostProcess>): List<RunningProcess> = DesktopLaunchers.running(hostProcesses())

    override fun stableId(path: String): String = path

    override fun isStableId(id: String): Boolean = false
}

object LinuxWindowChrome : WindowChrome {
    override fun roundCorners(window: Window) = Unit

    override fun limitWidth(window: Window, maxWidth: Int) = Unit
}

object LinuxFileShell : FileShell {
    override val executableIcon: ((path: String, size: Int) -> BufferedImage?)? = null
    override val revealInFolder: ((path: String) -> Unit)? = null
}
