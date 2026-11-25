package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NewAppsChannelPinTest {
    @Test
    fun aFreshInstallTakesThePlatformDefault() {
        assertEquals("VPN", NewAppsChannelPin.pinned(SettingsEntity(), upgrading = false, ConnectionType.VPN).newAppsConnectionType)
    }

    @Test
    fun anUpgradeKeepsDirectThatEarlierVersionsUsed() {
        assertEquals("DIRECT", NewAppsChannelPin.pinned(SettingsEntity(), upgrading = true, ConnectionType.VPN).newAppsConnectionType)
    }

    @Test
    fun aChosenChannelStays() {
        val chosen = SettingsEntity(newAppsConnectionType = "BYEDPI")
        assertEquals(chosen, NewAppsChannelPin.pinned(chosen, upgrading = true, ConnectionType.VPN))
        assertEquals(chosen, NewAppsChannelPin.pinned(chosen, upgrading = false, ConnectionType.VPN))
    }
}
