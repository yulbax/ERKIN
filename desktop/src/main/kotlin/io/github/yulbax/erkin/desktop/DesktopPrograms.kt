package io.github.yulbax.erkin.desktop

import java.io.File

data class RunningProcess(val executable: File, val startedByDesktop: Boolean)

sealed interface ProgramClassification {
    val hidesProcesses: Boolean

    fun isProcess(process: RunningProcess): Boolean

    fun isLaunchable(isProcess: Boolean): Boolean = !hidesProcesses || !isProcess

    data class ByLocation(val systemRoots: List<String>) : ProgramClassification {
        override val hidesProcesses: Boolean = false

        override fun isProcess(process: RunningProcess): Boolean =
            systemRoots.any { process.executable.path.startsWith(it, ignoreCase = true) }
    }

    data object ByLauncher : ProgramClassification {
        override val hidesProcesses: Boolean = true

        override fun isProcess(process: RunningProcess): Boolean = !process.startedByDesktop
    }

    companion object {
        fun forCurrentOs(): ProgramClassification =
            if (DesktopPaths.isWindows) ByLocation(listOfNotNull(System.getenv("SystemRoot"))) else ByLauncher
    }
}

object DesktopLaunchers {
    private const val SYSTEMD = "systemd"
    private const val APP_UNIT_PREFIX = "app-"
    private const val DELETED_SUFFIX = " (deleted)"
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
}
