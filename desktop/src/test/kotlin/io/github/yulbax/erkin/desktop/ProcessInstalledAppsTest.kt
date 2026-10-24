package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessInstalledAppsTest {

    private fun discover(vararg paths: String) =
        ProcessInstalledApps.discover(paths.map(::File), ownExecutable = "ERKIN", systemRoots = listOf("/usr/"))

    @Test
    fun aSingleCopyIsKeyedByNameAndShowsItsPath() {
        val apps = discover("/opt/telegram/Telegram", "/opt/telegram/Telegram", "/opt/erkin/bin/ERKIN")
        assertEquals(1, apps.size)
        assertEquals("Telegram", apps.single().packageName)
        assertEquals("/opt/telegram/Telegram", apps.single().path)
    }

    @Test
    fun copiesWithTheSameNameGetTheirOwnPathEntriesNextToTheNameEntry() {
        val apps = discover("/usr/bin/python3", "/home/u/venv/bin/python3")
        assertEquals(listOf("python3", "/usr/bin/python3", "/home/u/venv/bin/python3"), apps.map { it.packageName })
        assertEquals(listOf(null, "/usr/bin/python3", "/home/u/venv/bin/python3"), apps.map { it.path })
        assertEquals(listOf(true, true, false), apps.map { it.isSystemApp })
    }

    @Test
    fun macHelpersBelongToTheOutermostAppBundle() {
        val apps = discover(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Google Chrome.app/Contents/Frameworks/Google Chrome Framework.framework/Helpers/Google Chrome Helper.app/Contents/MacOS/Google Chrome Helper",
            "/Applications/ERKIN.app/Contents/MacOS/ERKIN",
            "/Applications/ERKIN.app/Contents/app/resources/ciadpi",
            "/opt/homebrew/bin/curl"
        )
        assertEquals(listOf("/Applications/Google Chrome.app", "curl"), apps.map { it.packageName })
        assertEquals(listOf("Google Chrome", "curl"), apps.map { it.name })
        assertEquals("/Applications/Google Chrome.app", apps.first().path)
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
            ownExecutable = "ERKIN",
            systemRoots = listOf("/usr/")
        )
        assertEquals(listOf("Hyprland"), apps.map { it.packageName })
    }
}
