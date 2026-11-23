package io.github.yulbax.erkin.desktop

import java.io.File

data class RunningProcess(
    val executable: File,
    val isProgram: Boolean,
    val ancestors: List<File> = emptyList(),
    val flatpak: String? = null
)

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

    fun running(): List<RunningProcess> {
        val windowed = if (this == WINDOWS) WindowsWindows.withWindows() else emptySet()
        val systemRoot = System.getenv("SystemRoot")
        return ProcessHandle.allProcesses().toList().mapNotNull { process ->
            process.info().command().orElse(null)?.let { command ->
                val executable = ProcessInstalledApps.executableOf(command)
                val flatpak = if (this == LINUX) FlatpakApps.of(process.pid()) else null
                RunningProcess(
                    executable = executable,
                    isProgram = when (this) {
                        LINUX -> flatpak != null || DesktopLaunchers.startedByDesktop(process)
                        WINDOWS -> process.pid() in windowed && systemRoot?.let { executable.path.startsWith(it, ignoreCase = true) } != true
                    },
                    ancestors = if (childrenInheritChannels && flatpak == null) DesktopLaunchers.ancestorsOf(process) else emptyList(),
                    flatpak = flatpak
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

    fun startedByDesktop(process: ProcessHandle): Boolean {
        val parentName = process.parent().flatMap { it.info().command() }.orElse(null)
            ?.let { File(it.removeSuffix(DELETED_SUFFIX)).name }
        return startedByDesktop(parentName, cgroupUnitOf(process.pid()))
    }

    private fun cgroupUnitOf(pid: Long): String? =
        runCatching { File("/proc/$pid/cgroup").readText().trim().substringAfterLast('/') }.getOrNull()

    fun ancestorsOf(process: ProcessHandle): List<File> =
        generateSequence(process.parent().orElse(null)) { it.parent().orElse(null) }
            .take(MAX_ANCESTORS)
            .takeWhile { it.pid() > 1 }
            .map { it.info().command().orElse(null)?.let { command -> File(command.removeSuffix(DELETED_SUFFIX)) } }
            .takeWhile { it != null && !isSessionRoot(it) }
            .filterNotNull()
            .toList()

    private fun isSessionRoot(executable: File): Boolean = executable.name in LAUNCHERS || executable.name in SESSION_ROOTS
}
