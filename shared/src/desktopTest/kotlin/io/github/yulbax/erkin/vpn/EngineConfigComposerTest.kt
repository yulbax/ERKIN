package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineConfigComposerTest {

    @Test(expected = IllegalStateException::class)
    fun perAppRoutingStillNeedsAnAssignedApp() {
        EngineConfigComposer.compose(inputs(apps = emptyList()), OWN, BYEDPI_PORT)
    }

    @Test
    fun byeDpiOnlyRoutingKeepsTheVpnChannelInactive() {
        val apps = listOf(App("org.example.dpi", "Dpi", isSystemApp = false, connectionType = ConnectionType.BYEDPI))
        val applied = EngineConfigComposer.compose(inputs(apps), OWN, BYEDPI_PORT)

        assertFalse(applied.vpnActive)
        assertTrue(applied.needsByeDpi)
    }

    @Test
    fun withoutServersByeDpiRunsAndVpnAppsGoDirect() {
        val apps = listOf(
            App("org.example.dpi", "Dpi", isSystemApp = false, connectionType = ConnectionType.BYEDPI),
            App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN),
            App("C:\\venv\\python.exe", "python", isSystemApp = false, connectionType = ConnectionType.VPN)
        )
        val applied = EngineConfigComposer.compose(noServers(apps), OWN, BYEDPI_PORT)

        assertTrue(applied.engineConfig.proxies.isEmpty())
        assertEquals(listOf("org.example.dpi"), applied.engineConfig.byeDpiPackages)
        assertTrue(applied.engineConfig.vpnPackages.isEmpty())
        assertEquals(listOf("C:\\venv\\python.exe"), applied.engineConfig.directPaths)
        assertFalse(applied.vpnActive)
    }

    @Test
    fun rulesToByeDpiStartItAndChangeTheRouting() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val plain = EngineConfigComposer.compose(inputs(apps), OWN, BYEDPI_PORT)
        val rule = RoutingRule(RuleMatch.DOMAIN_SUFFIX, "ru", RuleTarget.BYEDPI)
        val withRule = EngineConfigComposer.compose(inputs(apps).copy(rules = listOf(rule)), OWN, BYEDPI_PORT)

        assertFalse(plain.needsByeDpi)
        assertTrue(withRule.needsByeDpi)
        assertEquals(listOf(rule), withRule.engineConfig.rules)
        assertTrue(withRule.isStructuralChangeFrom(plain))
    }

    @Test
    fun countryRulesWaitForTheGeoIpDatabase() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val country = RoutingRule(RuleMatch.COUNTRY, "RU", RuleTarget.DIRECT)
        val domain = RoutingRule(RuleMatch.DOMAIN_SUFFIX, "ru", RuleTarget.DIRECT)
        val inputs = inputs(apps).copy(rules = listOf(country, domain))

        val missing = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT, geoIpReady = false)
        val ready = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT, geoIpReady = true)

        assertEquals(listOf(domain), missing.engineConfig.rules)
        assertEquals(listOf(country, domain), ready.engineConfig.rules)
        assertTrue(ready.isStructuralChangeFrom(missing))
    }

    @Test
    fun aChainDialsItsExitThroughTheEntry() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val base = inputs(apps)
        val exit = base.selected!!
        val entry = exit.copy(id = 2, name = "Entry", selected = false)
        fun chainOf(hop: ChainEntry) = ProfileEntity(id = 9, name = "Chain", type = ProxyChain.TYPE, link = "", outboundJson = ProxyChain(hop, exit.id).encode(), selected = true)

        val viaServer = EngineConfigComposer.compose(base.copy(selected = chainOf(ChainEntry.Server(entry.id)), profiles = listOf(exit, entry, chainOf(ChainEntry.Server(entry.id)))), OWN, BYEDPI_PORT)
        assertEquals(listOf("p2", "p9"), viaServer.engineConfig.proxies.map { it.tag })
        assertEquals("p2", viaServer.engineConfig.proxies.last().dialerProxy)
        assertEquals("p9", viaServer.selectedTag)
        assertFalse(viaServer.needsByeDpi)

        val viaByeDpi = EngineConfigComposer.compose(base.copy(selected = chainOf(ChainEntry.ByeDpi), profiles = listOf(exit, chainOf(ChainEntry.ByeDpi))), OWN, BYEDPI_PORT)
        assertEquals("byedpi", viaByeDpi.engineConfig.proxies.single().dialerProxy)
        assertTrue(viaByeDpi.needsByeDpi)
        assertTrue(viaByeDpi.isStructuralChangeFrom(viaServer))
    }

    @Test
    fun everyHopDialsThroughTheOneBeforeIt() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val base = inputs(apps)
        val exit = base.selected!!.copy(selected = false)
        val first = exit.copy(id = 2, name = "First")
        val second = exit.copy(id = 3, name = "Second")
        val chain = ProfileEntity(
            id = 9, name = "Chain", type = ProxyChain.TYPE, selected = true,
            outboundJson = ProxyChain(listOf(ChainEntry.ByeDpi, ChainEntry.Server(2), ChainEntry.Server(3)), exit.id).encode()
        )

        val applied = EngineConfigComposer.compose(base.copy(selected = chain, profiles = listOf(exit, first, second, chain)), OWN, BYEDPI_PORT)

        assertEquals(listOf("p2" to "byedpi", "p3" to "p2", "p9" to "p3"), applied.engineConfig.proxies.map { it.tag to it.dialerProxy })
        assertTrue(applied.needsByeDpi)
    }

    @Test(expected = IllegalStateException::class)
    fun aChainWithoutItsExitCannotStart() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val chain = ProfileEntity(id = 9, name = "Chain", type = ProxyChain.TYPE, link = "", outboundJson = ProxyChain(ChainEntry.ByeDpi, 42).encode(), selected = true)
        EngineConfigComposer.compose(inputs(apps).copy(selected = chain, profiles = listOf(chain)), OWN, BYEDPI_PORT)
    }

    @Test(expected = IllegalStateException::class)
    fun withoutServersVpnAppsAloneCannotStart() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        EngineConfigComposer.compose(noServers(apps), OWN, BYEDPI_PORT)
    }

    private fun noServers(apps: List<App>) =
        SessionInputs(selected = null, profiles = emptyList(), settings = SettingsEntity(), apps = apps)

    private fun inputs(apps: List<App>): SessionInputs {
        val parsed = requireNotNull(LinkParser.parse(VLESS))
        val profile = ProfileEntity(
            id = 1, name = parsed.name, type = parsed.protocol.wire, link = VLESS, outboundJson = parsed.proxyJson()
        )
        return SessionInputs(
            selected = profile,
            profiles = listOf(profile),
            settings = SettingsEntity(),
            apps = apps
        )
    }

    private companion object {
        const val OWN = "io.github.yulbax.erkin"
        const val BYEDPI_PORT = 30001
        const val VLESS = "vless://id@example.com:443?security=tls#Amsterdam"
    }
}
