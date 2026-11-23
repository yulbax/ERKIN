package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertFalse(curl.ownChannel)
    }

    @Test
    fun aProcessWithItsOwnChannelKeepsIt() {
        val ownRule = App("/usr/bin/curl", "curl", isSystemApp = true, connectionType = ConnectionType.BYEDPI, path = "/usr/bin/curl")
        val curl = appInfos(listOf(process("/usr/bin/curl", "/usr/bin/ghostty")), listOf(terminal, ownRule), ConnectionType.VPN).single()

        assertEquals(ConnectionType.BYEDPI, curl.connectionType)
        assertNull(curl.inheritedFrom)
        assertTrue(curl.ownChannel)
    }

    @Test
    fun anAncestorOnTheDefaultChannelPassesNothingDown() {
        val onDefault = terminal.copy(connectionType = ConnectionType.VPN)
        val curl = appInfos(listOf(process("/usr/bin/curl", "/usr/bin/ghostty")), listOf(onDefault), ConnectionType.VPN).single()

        assertEquals(ConnectionType.VPN, curl.connectionType)
        assertNull(curl.inheritedFrom)
        assertFalse(curl.ownChannel)
    }

    @Test
    fun anUnsavedProgramShowsTheChannelForNewApps() {
        val app = InstalledApp("C:\\Apps\\Zoom.exe", "Zoom", isSystemApp = false, isLaunchable = true, path = "C:\\Apps\\Zoom.exe")
        assertEquals(ConnectionType.DIRECT, appInfos(listOf(app), emptyList(), ConnectionType.DIRECT).single().connectionType)
    }
}
