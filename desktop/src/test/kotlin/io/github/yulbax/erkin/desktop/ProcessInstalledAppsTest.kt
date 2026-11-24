package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import java.io.File
import java.nio.file.Files
import io.github.yulbax.erkin.data.ConnectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessInstalledAppsTest {

    private fun discover(vararg paths: String, saved: List<App> = emptyList()) =
        ProcessInstalledApps.discover(paths.map { RunningProcess(File(it), isProgram = !it.startsWith("/usr/")) }, saved, own = OwnApp("ERKIN", null))

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
            listOf("/usr/bin/Hyprland (deleted)", "/usr/bin/Hyprland").map { RunningProcess(ProcessInstalledApps.executableOf(it), false) },
            saved = emptyList(),
            own = OwnApp("ERKIN", null)
        )
        assertEquals(listOf("/usr/bin/Hyprland"), apps.map { it.packageName })
    }

    @Test
    fun onLinuxOnlyWhatTheDesktopStartedIsAProgram() {
        val apps = ProcessInstalledApps.discover(
            listOf(
                RunningProcess(File("/usr/bin/ghostty"), isProgram = true),
                RunningProcess(File("/usr/bin/fish"), isProgram = false),
                RunningProcess(File("/opt/Discord/Discord"), isProgram = true),
                RunningProcess(File("/opt/Discord/Discord"), isProgram = false)
            ),
            saved = emptyList(),
            own = OwnApp("ERKIN", null)
        )
        assertEquals(listOf(false, true, false), apps.map { it.isSystemApp })
        assertEquals(listOf(true, false, true), apps.map { it.isLaunchable })
    }

    @Test
    fun theDesktopStartsProgramsDirectlyOrThroughItsOwnSystemdUnits() {
        assertEquals(true, DesktopLaunchers.startedByDesktop("Hyprland", "session-3.scope"))
        assertEquals(true, DesktopLaunchers.startedByDesktop("systemd", "app-org.kde.dolphin@1a2b.service"))
        assertEquals(false, DesktopLaunchers.startedByDesktop("systemd", "pipewire.service"))
        assertEquals(false, DesktopLaunchers.startedByDesktop("ghostty", "app-ghostty-surface-transient-42.scope"))
        assertEquals(false, DesktopLaunchers.startedByDesktop(null, null))
    }

    @Test
    fun theListShowsProgramsInstalledAppsAndAnythingWithItsOwnChannel() {
        fun installed(path: String, isProcess: Boolean) = io.github.yulbax.erkin.data.InstalledApp(path, File(path).name, isProcess, !isProcess, path)
        val running = listOf(installed("/usr/bin/ghostty", false), installed("/usr/bin/fish", true), installed("/usr/bin/curl", true))
        val saved = listOf(
            App("/usr/bin/curl", "curl", isSystemApp = true, connectionType = ConnectionType.BYEDPI, path = "/usr/bin/curl"),
            App("/usr/lib/firefox/firefox", "firefox", isSystemApp = true, connectionType = ConnectionType.VPN, path = "/usr/lib/firefox/firefox"),
            App("/home/u/tool", "tool", isSystemApp = false, connectionType = ConnectionType.VPN, path = "/home/u/tool")
        )
        val catalog = listOf(
            CatalogProgram.executable("Firefox", File("/usr/lib/firefox/firefox")),
            CatalogProgram.executable("Ghostty", File("/usr/bin/ghostty")),
            CatalogProgram.executable("GIMP", File("/usr/bin/gimp")),
            CatalogProgram.executable("Foot Server", File("/usr/bin/foot")),
            CatalogProgram.executable("Foot", File("/usr/bin/foot"))
        )
        val apps = ProcessInstalledApps.listed(running, saved, catalog, ConnectionType.VPN, own = OwnApp("ERKIN", null))

        assertEquals(listOf("Firefox", "Foot", "GIMP", "Ghostty", "curl"), apps.map { it.name }.sorted())
        assertTrue(apps.all { it.isRegular })
    }

    @Test
    fun aFlatpakAppIsOneEntryUnderItsIdWhetherOrNotItRuns() {
        val running = ProcessInstalledApps.discover(
            listOf(
                RunningProcess(File("/usr/bin/bwrap"), isProgram = true, flatpak = "org.telegram.desktop"),
                RunningProcess(File("/app/bin/Telegram"), isProgram = true, flatpak = "org.telegram.desktop"),
                RunningProcess(File("/usr/bin/xdg-dbus-proxy"), isProgram = true, flatpak = "org.telegram.desktop")
            ),
            saved = emptyList(),
            own = OwnApp("ERKIN", null)
        )
        assertEquals(listOf("org.telegram.desktop"), running.map { it.packageName })
        assertNull(running.single().path)

        val catalog = listOf(
            CatalogProgram.flatpak("Telegram", "org.telegram.desktop"),
            CatalogProgram.flatpak("Discord", "com.discordapp.Discord")
        )
        val apps = ProcessInstalledApps.listed(running, emptyList(), catalog, ConnectionType.VPN, own = OwnApp("ERKIN", null))
        assertEquals(
            listOf("Discord" to "com.discordapp.Discord", "Telegram" to "org.telegram.desktop"),
            apps.map { it.name to it.packageName }.sortedBy { it.first }
        )
    }

    @Test
    fun theHostTableTellsProgramsTheirAncestorsAndFlatpakApps() {
        val table = listOf(
            HostProcess(1, 0, null, "systemd"),
            HostProcess(10, 1, "/usr/bin/Hyprland", "Hyprland", "session-3.scope"),
            HostProcess(20, 10, "/usr/bin/ghostty", "ghostty", "session-3.scope"),
            HostProcess(30, 20, "/usr/bin/fish", "fish", "session-3.scope"),
            HostProcess(40, 30, "/usr/bin/curl", "curl", "session-3.scope"),
            HostProcess(45, 10, "/usr/bin/bwrap", "bwrap", "app-flatpak-org.telegram.desktop-1.scope", "org.telegram.desktop"),
            HostProcess(50, 45, "/app/bin/Telegram", "Telegram", "app-flatpak-org.telegram.desktop-1.scope", "org.telegram.desktop")
        )
        val running = DesktopLaunchers.running(table).associateBy { it.executable.path }

        assertEquals(setOf("/usr/bin/Hyprland", "/usr/bin/ghostty", "/usr/bin/fish", "/usr/bin/curl", "/usr/bin/bwrap", "/app/bin/Telegram"), running.keys)
        assertTrue(running.getValue("/usr/bin/ghostty").isProgram)
        assertEquals(false, running.getValue("/usr/bin/fish").isProgram)
        assertEquals(listOf("/usr/bin/fish", "/usr/bin/ghostty"), running.getValue("/usr/bin/curl").ancestors.map { it.path })
        assertEquals(listOf("org.telegram.desktop", "org.telegram.desktop"), listOf(running.getValue("/usr/bin/bwrap").flatpak, running.getValue("/app/bin/Telegram").flatpak))
        assertTrue(running.getValue("/app/bin/Telegram").isProgram)
    }

    @Test
    fun erkinDoesNotListItselfByExecutableOrFlatpakId() {
        val own = OwnApp("ERKIN", "io.github.yulbax.erkin")
        assertTrue(own.isOwn("io.github.yulbax.erkin"))
        assertTrue(own.isOwn("/opt/erkin/bin/ERKIN"))
        assertEquals(false, own.isOwn("org.telegram.desktop"))
    }
}
