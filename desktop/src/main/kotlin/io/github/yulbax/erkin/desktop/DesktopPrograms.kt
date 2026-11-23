package io.github.yulbax.erkin.desktop

import java.io.File

data class RunningProcess(val executable: File, val startedByDesktop: Boolean, val ancestors: List<File> = emptyList())

sealed interface ProgramClassification {
    fun isProcess(process: RunningProcess): Boolean

    fun isLaunchable(isProcess: Boolean): Boolean = !isProcess

    val childrenInheritChannels: Boolean get() = false

    data class ByLocation(val systemRoots: List<String>) : ProgramClassification {
        override fun isProcess(process: RunningProcess): Boolean =
            systemRoots.any { process.executable.path.startsWith(it, ignoreCase = true) }
    }

    data object ByLauncher : ProgramClassification {
        override fun isProcess(process: RunningProcess): Boolean = !process.startedByDesktop

        override val childrenInheritChannels: Boolean get() = true
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
