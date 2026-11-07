package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnFailureTest {

    @Test
    fun startingWithoutServersOrAppsReportsWhatIsMissing() {
        assertEquals(VpnFailure.NoServers, failureOf(SessionInputs(null, emptyList(), SettingsEntity(), listOf(App("org.vpn", "Vpn", false, ConnectionType.VPN)))))
        val server = ProfileEntity(id = 1, name = "a", selected = true)
        assertEquals(VpnFailure.NoRoutedApps, failureOf(SessionInputs(server, listOf(server), SettingsEntity(), listOf(App("org.bank", "Bank", false, ConnectionType.BYPASS)))))
        assertEquals(VpnFailure.NoServerSelected, failureOf(SessionInputs(null, listOf(server), SettingsEntity(), emptyList())))
    }

    @Test
    fun aChainWhoseServerIsGoneIsNamedInTheFailure() {
        val chain = ProfileEntity(id = 9, name = "Home", type = ProxyChain.TYPE, selected = true, outboundJson = ProxyChain(ChainEntry.Server(5), 6).encode())
        val apps = listOf(App("org.vpn", "Vpn", false, ConnectionType.VPN))

        assertEquals(VpnFailure.ChainServerRemoved("Home"), failureOf(SessionInputs(chain, listOf(chain), SettingsEntity(), apps)))
    }

    @Test
    fun foreignErrorsKeepTheirTextAndTheKernelModulesMarkerIsRecognised() {
        assertEquals(VpnFailure.ProcessLookupUnavailable, VpnFailure.of(IllegalStateException("start: ${VpnFailure.PROCESS_LOOKUP_UNAVAILABLE}")))
        assertEquals(VpnFailure.Other("dial tcp: timeout"), VpnFailure.of(RuntimeException("dial tcp: timeout")))
        assertEquals(VpnFailure.TunUnavailable, VpnFailure.of(VpnFailureException(VpnFailure.TunUnavailable)))
    }

    private fun failureOf(inputs: SessionInputs): VpnFailure =
        VpnFailure.of(runCatching { EngineConfigComposer.compose(inputs, "own", 1080) }.exceptionOrNull() ?: error("compose should fail"))
}
