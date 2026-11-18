package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessInstalledAppsTest {

    private fun discover(vararg paths: String, saved: List<App> = emptyList()) =
        ProcessInstalledApps.discover(paths.map(::File), saved, ownExecutable = "ERKIN", systemRoots = listOf("/usr/"))

    @Test
    fun aProgramIsKeyedByItsPath() {
        val apps = discover("/opt/telegram/Telegram", "/opt/telegram/Telegram", "/opt/erkin/bin/ERKIN")
        assertEquals(listOf("/opt/telegram/Telegram"), apps.map { it.packageName })
        assertEquals(listOf("Telegram"), apps.map { it.name })
    }

    @Test
    fun copiesWithTheSameNameAreSeparatePrograms() {
        val apps = discover("/usr/bin/python3", "/home/u/venv/bin/python3")
        assertEquals(listOf("/usr/bin/python3", "/home/u/venv/bin/python3"), apps.map { it.packageName })
        assertEquals(listOf(true, false), apps.map { it.isSystemApp })
    }

    @Test
    fun aProgramSavedByNameKeepsItsEntryOnlyAtItsOwnPath() {
        val saved = listOf(App("Telegram", "Telegram", isSystemApp = false, path = "/opt/telegram/Telegram"))
        val apps = discover("/opt/telegram/Telegram", "/tmp/x/Telegram", saved = saved)
        assertEquals(listOf("Telegram", "/tmp/x/Telegram"), apps.map { it.packageName })
        assertEquals(listOf("/opt/telegram/Telegram", "/tmp/x/Telegram"), apps.map { it.path })
    }

    @Test
    fun removedProgramsAreForgottenButUnmountedDrivesAndUncheckedEntriesStay() {
        val root = Files.createTempDirectory("erkin-apps").toFile()
        try {
            val installed = File(root, "opt/telegram/Telegram").apply { parentFile.mkdirs(); writeText("") }
            File(root, "opt/other").mkdirs()
            val unmounted = File(root, "mnt/drive").apply { mkdirs() }
            fun app(id: String, path: String?) = App(id, File(id).name, isSystemApp = false, path = path)
            val saved = listOf(
                app("Telegram", installed.path),
                app("zen-bin", File(root, "opt/zen-browser-bin/zen-bin").path),
                app("game", File(unmounted, "games/game").path),
                app("Hyprland (deleted)", File(root, "usr/bin/Hyprland (deleted)").path),
                app("FRKN (deleted)", null),
                app("python3", null),
                app("running", File(root, "opt/gone/running").path)
            )

            val removed = ProcessInstalledApps.removedApps(saved, runningIds = setOf("running"))

            assertEquals(listOf("zen-bin", "Hyprland (deleted)", "FRKN (deleted)"), removed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aProgramUpdatedWhileRunningIsTheSameProgram() {
        val apps = ProcessInstalledApps.discover(
            listOf("/usr/bin/Hyprland (deleted)", "/usr/bin/Hyprland").map(ProcessInstalledApps::executableOf),
            saved = emptyList(),
            ownExecutable = "ERKIN",
            systemRoots = listOf("/usr/")
        )
        assertEquals(listOf("/usr/bin/Hyprland"), apps.map { it.packageName })
    }
}
