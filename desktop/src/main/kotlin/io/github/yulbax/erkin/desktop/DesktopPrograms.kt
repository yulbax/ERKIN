package io.github.yulbax.erkin.desktop

import java.io.File

data class RunningProcess(
    val executable: File,
    val isProgram: Boolean,
    val ancestors: List<File> = emptyList(),
    val flatpak: String? = null
)

data class HostProcess(
    val pid: Long,
    val parent: Long,
    val exe: String?,
    val name: String,
    val unit: String? = null,
    val flatpak: String? = null
)

class OwnApp(private val executable: String?, private val flatpak: String?) {
    fun isOwn(id: String): Boolean = id == flatpak || File(id).name == executable
}

data class CatalogProgram(val name: String, val id: String, val path: String?, val icon: File? = null) {
    companion object {
        fun executable(name: String, executable: File, icon: File? = null) =
            CatalogProgram(name, WindowsAppIds.stable(executable.path), executable.path, icon)

        fun flatpak(name: String, app: String, icon: File? = null) = CatalogProgram(name, app, null, icon)
    }
}

enum class DesktopPrograms(val childrenInheritChannels: Boolean) {
    LINUX(childrenInheritChannels = true),
    WINDOWS(childrenInheritChannels = false);

    fun catalog(): List<CatalogProgram> = when (this) {
        LINUX -> LinuxApplications.list()
        WINDOWS -> WindowsStartApps.list()
    }

    fun running(hostProcesses: () -> List<HostProcess>): List<RunningProcess> = when (this) {
        LINUX -> DesktopLaunchers.running(hostProcesses())
        WINDOWS -> windowsPrograms()
    }

    private fun windowsPrograms(): List<RunningProcess> {
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

    companion object {
        val current: DesktopPrograms get() = if (DesktopPaths.isWindows) WINDOWS else LINUX
    }
}

object DesktopLaunchers {
    private const val SYSTEMD = "systemd"
    private const val APP_UNIT_PREFIX = "app-"
    private const val DELETED_SUFFIX = " (deleted)"
    private const val MAX_ANCESTORS = 64
    private val SESSION_ROOTS = setOf("systemd", "init", "erkin-service", "login")
    private val LAUNCHERS = setOf(
        "Hyprland", "hyprland", "sway", "river", "niri", "labwc", "wayfire",
        "kwin_wayland", "kwin_x11", "plasmashell", "krunner", "ksmserver",
        "gnome-shell", "xfce4-panel", "xfce4-session", "cinnamon", "mate-panel", "lxqt-panel",
        "rofi", "wofi", "fuzzel", "tofi", "walker", "anyrun", "ulauncher", "albert"
    )

    fun startedByDesktop(parentName: String?, cgroupUnit: String?): Boolean = when (parentName) {
        null -> false
        in LAUNCHERS -> true
        SYSTEMD -> cgroupUnit?.startsWith(APP_UNIT_PREFIX) == true
        else -> false
    }

    fun running(processes: List<HostProcess>): List<RunningProcess> {
        val byPid = processes.associateBy { it.pid }
        return processes.mapNotNull { process ->
            val exe = process.exe ?: return@mapNotNull null
            RunningProcess(
                executable = ProcessInstalledApps.executableOf(exe),
                isProgram = process.flatpak != null || startedByDesktop(byPid[process.parent]?.name, process.unit),
                ancestors = if (process.flatpak == null) ancestorsOf(process, byPid) else emptyList(),
                flatpak = process.flatpak
            )
        }
    }

    private fun ancestorsOf(process: HostProcess, byPid: Map<Long, HostProcess>): List<File> =
        generateSequence(byPid[process.parent]) { byPid[it.parent] }
            .take(MAX_ANCESTORS)
            .takeWhile { it.pid > 1 }
            .map { it.exe?.let { exe -> File(exe.removeSuffix(DELETED_SUFFIX)) } }
            .takeWhile { it != null && !isSessionRoot(it) }
            .filterNotNull()
            .toList()

    private fun isSessionRoot(executable: File): Boolean = executable.name in LAUNCHERS || executable.name in SESSION_ROOTS
}
