package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.mihomo.DesktopOs
import io.github.yulbax.erkin.vpn.mihomo.SystemDns
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File

interface DesktopSystem {
    val desktopOs: DesktopOs
    val deviceOsName: String
    val newAppsChannel: ConnectionType
    val ciadpiUnsupportedOptions: Set<String>
    val installedServiceSocket: File
    val sandboxApp: String?

    fun executableName(base: String): String

    fun instanceSocket(dataDir: File): File

    fun userDataDir(windowsName: String, unixName: String): File

    fun hostDataDir(unixName: String): File?
}

interface ServiceHost {
    val sandboxProtectSocket: File?
    val serviceCommand: List<String>

    fun startInstalled(log: AppLog): Boolean

    val protectSocket: File
        get() = sandboxProtectSocket
            ?: if (DesktopPaths.isPortable) File(System.getenv("TEMP") ?: System.getProperty("user.home"), "erkin-${ProcessHandle.current().pid()}.sock")
            else File(DesktopPaths.serviceSocket.parentFile, PROTECT_SOCKET)

    val runsServiceItself: Boolean
        get() = DesktopPaths.isPortable || sandboxProtectSocket != null
}

const val PROTECT_SOCKET = "protect.sock"

interface TrayHost {
    val isAvailable: Boolean
}

interface WindowChrome {
    fun roundCorners(window: Window)

    fun limitWidth(window: Window, maxWidth: Int)
}

interface SystemDnsSource {
    fun detect(): SystemDns
}

interface FileShell {
    val executableIcon: ((path: String, size: Int) -> BufferedImage?)?
    val revealInFolder: ((path: String) -> Unit)?
}
