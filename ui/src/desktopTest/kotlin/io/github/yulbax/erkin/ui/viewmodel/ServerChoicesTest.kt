package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.vpn.ProxyRef
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerChoicesTest {

    private val entry = ProfileEntity(id = 1, name = "Amazon")
    private val exit = ProfileEntity(id = 2, name = "Финляндия")
    private val chain = ProfileEntity(id = 9, name = "Дом", type = ProxyChain.TYPE, outboundJson = ProxyChain(listOf(ChainEntry.ByeDpi, ChainEntry.Server(1)), 2).encode())
    private val choices = ServerChoices(listOf(entry, exit, chain), unreachable = setOf(1L))

    @Test
    fun aChainIsNamedAfterItsExitEverywhere() {
        assertEquals(ServerLabel("Финляндия", isChain = true), choices.labelOf(ProxyRef.Profile(9)))
    }

    @Test
    fun aHopIsNamedAfterItsOwnServerAndKeepsItsWarning() {
        assertEquals(ServerLabel("Amazon", isChain = false, unreachable = true), choices.labelOf(ProxyRef.Hop(9, 1)))
        assertEquals(null, choices.labelOf(ProxyRef.Hop(9, 0)))
    }

    @Test
    fun aServerChosenOnPurposeIsShownByNameEvenWhileItIsTheMainOne() {
        val main = ProfileEntity(id = 3, name = "Amsterdam", selected = true)
        val withMain = ServerChoices(listOf(main, exit))

        assertEquals(CurrentServer.Bound(3), withMain.current(ConnectionType.VPN, 3))
        assertEquals(ServerLabel("Amsterdam", isChain = false), withMain.label(ConnectionType.VPN, 3))
        assertEquals(CurrentServer.Main, withMain.current(ConnectionType.VPN, null))
        assertEquals(null, withMain.label(ConnectionType.VPN, null))
    }
}
