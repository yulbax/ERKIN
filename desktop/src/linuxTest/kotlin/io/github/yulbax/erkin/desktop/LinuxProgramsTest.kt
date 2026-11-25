package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxProgramsTest {

    @Test
    fun theDesktopStartsProgramsDirectlyOrThroughItsOwnSystemdUnits() {
        assertEquals(true, DesktopLaunchers.startedByDesktop("Hyprland", "session-3.scope"))
        assertEquals(true, DesktopLaunchers.startedByDesktop("systemd", "app-org.kde.dolphin@1a2b.service"))
        assertEquals(false, DesktopLaunchers.startedByDesktop("systemd", "pipewire.service"))
        assertEquals(false, DesktopLaunchers.startedByDesktop("ghostty", "app-ghostty-surface-transient-42.scope"))
        assertEquals(false, DesktopLaunchers.startedByDesktop(null, null))
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
            own = OwnApp("ERKIN", null),
            programs = LinuxPrograms
        )
        assertEquals(listOf("org.telegram.desktop"), running.map { it.packageName })
        assertNull(running.single().path)

        val catalog = listOf(
            CatalogProgram.flatpak("Telegram", "org.telegram.desktop"),
            CatalogProgram.flatpak("Discord", "com.discordapp.Discord")
        )
        val apps = ProcessInstalledApps.listed(running, emptyList(), catalog, ConnectionType.VPN, own = OwnApp("ERKIN", null), programs = LinuxPrograms)
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
}
