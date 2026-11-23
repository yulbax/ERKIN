package io.github.yulbax.erkin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelUseTest {
    private val none = RoutedApps.from(emptyList())

    @Test
    fun aFreshInstallWhereNewAppsGoThroughTheVpnCanConnect() {
        assertEquals(ChannelUse(any = true, vpn = true, byeDpi = false, withoutServer = false), ChannelUse.of(none, ConnectionType.VPN))
    }

    @Test
    fun newAppsOnByeDpiRunWithoutAServer() {
        assertEquals(ChannelUse(any = true, vpn = false, byeDpi = true, withoutServer = true), ChannelUse.of(none, ConnectionType.BYEDPI))
    }

    @Test
    fun newAppsGoingStraightOutGiveNothingToConnect() {
        assertEquals(ChannelUse(any = false, vpn = false, byeDpi = false, withoutServer = false), ChannelUse.of(none, ConnectionType.DIRECT))
    }

    @Test
    fun savedAppsCountWhateverTheNewAppsChannel() {
        val routed = RoutedApps.from(listOf(App("org.example", "Example", isSystemApp = false, connectionType = ConnectionType.VPN)))
        assertEquals(ChannelUse(any = true, vpn = true, byeDpi = false, withoutServer = false), ChannelUse.of(routed, ConnectionType.DIRECT))
    }
}
