package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppChannelsTest {
    private val terminal = App("/usr/bin/ghostty", "ghostty", isSystemApp = false, connectionType = ConnectionType.DIRECT, path = "/usr/bin/ghostty")

    private fun process(path: String, vararg ancestors: String) =
        InstalledApp(path, path.substringAfterLast('/'), isSystemApp = true, isLaunchable = false, path = path, ancestors = ancestors.toList())

    @Test
    fun aCommandShowsTheChannelOfTheTerminalItRunsIn() {
        val curl = appInfos(listOf(process("/usr/bin/curl", "/usr/bin/fish", "/usr/bin/ghostty")), listOf(terminal), ConnectionType.VPN).single()

        assertEquals(ConnectionType.DIRECT, curl.connectionType)
        assertEquals("ghostty", curl.inheritedFrom)
    }

    @Test
    fun aProcessWithItsOwnChannelKeepsIt() {
        val ownRule = App("/usr/bin/curl", "curl", isSystemApp = true, connectionType = ConnectionType.BYEDPI, path = "/usr/bin/curl")
        val curl = appInfos(listOf(process("/usr/bin/curl", "/usr/bin/ghostty")), listOf(terminal, ownRule), ConnectionType.VPN).single()

        assertEquals(ConnectionType.BYEDPI, curl.connectionType)
        assertNull(curl.inheritedFrom)
    }

    @Test
    fun anAncestorOnTheDefaultChannelPassesNothingDown() {
        val onDefault = terminal.copy(connectionType = ConnectionType.VPN)
        val curl = appInfos(listOf(process("/usr/bin/curl", "/usr/bin/ghostty")), listOf(onDefault), ConnectionType.VPN).single()

        assertEquals(ConnectionType.VPN, curl.connectionType)
        assertNull(curl.inheritedFrom)
    }

    @Test
    fun anUnsavedProgramShowsTheChannelForNewApps() {
        val app = InstalledApp("C:\\Apps\\Zoom.exe", "Zoom", isSystemApp = false, isLaunchable = true, path = "C:\\Apps\\Zoom.exe")
        assertEquals(ConnectionType.DIRECT, appInfos(listOf(app), emptyList(), ConnectionType.DIRECT).single().connectionType)
    }

    @Test
    fun aConnectionFindsItsAppByIdOrByPath() {
        val apps = appInfos(
            listOf(
                InstalledApp("org.telegram.desktop", "Telegram", isSystemApp = false, isLaunchable = true, path = null),
                InstalledApp("/opt/zen-browser-bin/zen-bin", "Zen Browser", isSystemApp = false, isLaunchable = true, path = "/opt/zen-browser-bin/zen-bin"),
                InstalledApp("C:\\Apps\\Tool.exe", "Tool", isSystemApp = false, isLaunchable = true, path = "C:\\Apps\\Tool.exe")
            ),
            listOf(App("org.telegram.desktop", "Telegram", isSystemApp = false, connectionType = ConnectionType.DIRECT)),
            ConnectionType.VPN
        )
        val lookup = AppLookup(apps)

        val telegram = lookup.find("org.telegram.desktop", "/app/bin/Telegram")
        assertEquals("Telegram" to null, telegram?.name to telegram?.path)
        assertEquals(ConnectionType.DIRECT, telegram?.connectionType)
        assertEquals("Zen Browser", lookup.find("zen-bin", "/opt/zen-browser-bin/zen-bin")?.name)
        assertEquals("Tool", lookup.find("Tool.exe", "c:\\apps\\tool.exe")?.name)
        assertNull(lookup.find("curl", "/usr/bin/curl"))
        assertNull(lookup.find("curl", ""))
    }
}
