package io.github.yulbax.erkin.desktop

import java.io.File

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
