package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.RoutedApps
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.rulelist.InstalledRuleList
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleSetKind
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
    fun directAppsEnterTheTunAndBypassAppsStayOutside() {
        val vpn = App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN)
        val bypass = App("org.example.bank", "Bank", isSystemApp = false, connectionType = ConnectionType.BYPASS)
        val bypassed = EngineConfigComposer.compose(inputs(listOf(vpn, bypass)), OWN, BYEDPI_PORT)
        val direct = EngineConfigComposer.compose(inputs(listOf(vpn, bypass.copy(connectionType = ConnectionType.DIRECT))), OWN, BYEDPI_PORT)

        assertEquals(listOf("org.example.vpn"), bypassed.engineConfig.tunneledPackages)
        assertEquals(listOf("org.example.vpn", "org.example.bank"), direct.engineConfig.tunneledPackages)
        assertEquals(listOf("org.example.bank"), direct.engineConfig.directPackages)
        assertTrue(direct.isStructuralChangeFrom(bypassed))
    }

    @Test
    fun aRuleBoundToAnotherServerAddsItsProxyAndChangesTheRouting() {
        val app = App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN)
        val base = inputs(listOf(app))
        val other = base.profiles.single().copy(id = 2, selected = false, link = "$VLESS-other")
        val free = base.copy(profiles = base.profiles + other, rules = listOf(RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN)))
        val bound = free.copy(rules = listOf(RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN, serverId = 2)))

        val before = EngineConfigComposer.compose(free, OWN, BYEDPI_PORT)
        val after = EngineConfigComposer.compose(bound, OWN, BYEDPI_PORT)

        assertEquals(listOf("p1"), before.engineConfig.proxies.map { it.tag })
        assertEquals(listOf("p1", "p2"), after.engineConfig.proxies.map { it.tag })
        assertEquals(mapOf(2L to "p2"), after.engineConfig.serverTags)
        assertTrue(after.isStructuralChangeFrom(before))
    }

    @Test
    fun blockedAppsEnterTheTunAndChangeTheRouting() {
        val vpn = App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN)
        val blocked = App("org.example.ads", "Ads", isSystemApp = false, connectionType = ConnectionType.BLOCK)
        val before = EngineConfigComposer.compose(inputs(listOf(vpn, blocked.copy(connectionType = ConnectionType.DIRECT))), OWN, BYEDPI_PORT)
        val after = EngineConfigComposer.compose(inputs(listOf(vpn, blocked)), OWN, BYEDPI_PORT)

        assertEquals(listOf("org.example.ads"), after.engineConfig.blockedPackages)
        assertEquals(listOf("org.example.ads", "org.example.vpn"), after.engineConfig.tunneledPackages)
        assertTrue(after.isStructuralChangeFrom(before))
    }

    @Test
    fun blockedAppsRunWithoutAServer() {
        val apps = listOf(App("org.example.ads", "Ads", isSystemApp = false, connectionType = ConnectionType.BLOCK))
        val config = EngineConfigComposer.compose(inputs(apps).copy(selected = null, profiles = emptyList()), OWN, BYEDPI_PORT)

        assertEquals(listOf("org.example.ads"), config.engineConfig.tunneledPackages)
        assertFalse(config.vpnActive)
        assertFalse(config.needsByeDpi)
        assertTrue(RoutedApps.from(apps, hasServers = false).runsWithoutServer)
    }

    @Test
    fun blockIsNotAChannelForNewApps() {
        assertEquals(ConnectionType.VPN, SettingsEntity(newAppsConnectionType = "BLOCK").newAppsConnectionType(ConnectionType.VPN))
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
    fun ruleSetRulesWaitForTheirFiles() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val country = RoutingRule(RuleMatch.RULE_SET, RuleLists.country("RU").url, RuleTarget.DIRECT)
        val domain = RoutingRule(RuleMatch.DOMAIN_SUFFIX, "ru", RuleTarget.DIRECT)
        val inputs = inputs(apps).copy(rules = listOf(country, domain))

        val countryUrl = country.value
        val missing = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT)
        val ready = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT) { url ->
            InstalledRuleList(setOf(RuleSetKind.IPCIDR), 1).takeIf { url == countryUrl }
        }

        assertEquals(listOf(domain), missing.engineConfig.rules)
        assertEquals(listOf(country, domain), ready.engineConfig.rules)
        assertEquals(mapOf(countryUrl to setOf(RuleSetKind.IPCIDR)), ready.engineConfig.ruleLists)
        assertTrue(ready.isStructuralChangeFrom(missing))
    }

    @Test
    fun listRulesWaitForTheirFilesAndReloadWhenUpdated() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val list = RoutingRule(RuleMatch.RULE_SET, "https://example.com/ads.txt", RuleTarget.BLOCK)
        val inputs = inputs(apps).copy(rules = listOf(list))
        fun installed(at: Long) = InstalledRuleList(setOf(RuleSetKind.DOMAIN), at)

        val missing = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT)
        val ready = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT) { installed(1) }
        val updated = EngineConfigComposer.compose(inputs, OWN, BYEDPI_PORT) { installed(2) }

        assertEquals(emptyList<RoutingRule>(), missing.engineConfig.rules)
        assertEquals(listOf(list), ready.engineConfig.rules)
        assertEquals(mapOf(list.value to setOf(RuleSetKind.DOMAIN)), ready.engineConfig.ruleLists)
        assertTrue(ready.isStructuralChangeFrom(missing))
        assertTrue(updated.isStructuralChangeFrom(ready))
    }

    @Test
    fun aChainDialsItsExitThroughTheEntry() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val base = inputs(apps)
        val exit = base.selected!!
        val entry = exit.copy(id = 2, name = "Entry", selected = false)
        fun chainOf(hop: ChainEntry) = ProfileEntity(id = 9, name = "Chain", type = ProxyChain.TYPE, link = "", outboundJson = ProxyChain(hop, exit.id).encode(), selected = true)

        val viaServer = EngineConfigComposer.compose(base.copy(selected = chainOf(ChainEntry.Server(entry.id)), profiles = listOf(exit, entry, chainOf(ChainEntry.Server(entry.id)))), OWN, BYEDPI_PORT)
        assertEquals(listOf("p9h0", "p9"), viaServer.engineConfig.proxies.map { it.tag })
        assertEquals("p9h0", viaServer.engineConfig.proxies.last().dialerProxy)
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

        assertEquals(listOf("p9h1" to "byedpi", "p9h2" to "p9h1", "p9" to "p9h2"), applied.engineConfig.proxies.map { it.tag to it.dialerProxy })
        assertTrue(applied.needsByeDpi)
    }

    @Test
    fun appsBoundToAnotherServerGetItAlongsideTheMainOne() {
        val base = inputs(emptyList())
        val main = base.selected!!.copy(selected = true)
        val other = main.copy(id = 2, name = "Other", selected = false)
        val gone = 42L
        val apps = listOf(
            App("org.example.main", "Main", isSystemApp = false, connectionType = ConnectionType.VPN),
            App("org.example.other", "Other", isSystemApp = false, connectionType = ConnectionType.VPN, serverId = other.id),
            App("org.example.self", "Self", isSystemApp = false, connectionType = ConnectionType.VPN, serverId = main.id),
            App("org.example.gone", "Gone", isSystemApp = false, connectionType = ConnectionType.VPN, serverId = gone)
        )

        val applied = EngineConfigComposer.compose(base.copy(selected = main, profiles = listOf(main, other), apps = apps), OWN, BYEDPI_PORT)

        assertEquals(listOf("p1", "p2"), applied.engineConfig.proxies.map { it.tag })
        assertEquals(mapOf("org.example.other" to "p2"), applied.engineConfig.vpnTargets)
        assertEquals("p1", applied.selectedTag)

        val unbound = EngineConfigComposer.compose(
            base.copy(selected = main, profiles = listOf(main, other), apps = apps.map { it.copy(serverId = null) }),
            OWN, BYEDPI_PORT
        )
        assertTrue(applied.isStructuralChangeFrom(unbound))
    }

    @Test(expected = IllegalStateException::class)
    fun aChainWithoutItsExitCannotStart() {
        val apps = listOf(App("org.example.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN))
        val chain = ProfileEntity(id = 9, name = "Chain", type = ProxyChain.TYPE, link = "", outboundJson = ProxyChain(ChainEntry.ByeDpi, 42).encode(), selected = true)
        EngineConfigComposer.compose(inputs(apps).copy(selected = chain, profiles = listOf(chain)), OWN, BYEDPI_PORT)
    }

    @Test
    fun directAppsAloneStartWithoutAServer() {
        val apps = listOf(App("org.example.direct", "Direct", isSystemApp = false, connectionType = ConnectionType.DIRECT))
        val config = EngineConfigComposer.compose(noServers(apps), OWN, BYEDPI_PORT)

        assertEquals(listOf("org.example.direct"), config.engineConfig.tunneledPackages)
        assertFalse(config.vpnActive)
    }

    @Test
    fun aRuleToTheVpnTurnsOnTheVpnCheckForDirectApps() {
        val apps = listOf(App("org.example.direct", "Direct", isSystemApp = false, connectionType = ConnectionType.DIRECT))
        val plain = EngineConfigComposer.compose(inputs(apps), OWN, BYEDPI_PORT)
        val ruled = EngineConfigComposer.compose(
            inputs(apps).copy(rules = listOf(RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN, app = "org.example.direct"))),
            OWN,
            BYEDPI_PORT
        )

        assertFalse(plain.vpnActive)
        assertTrue(ruled.vpnActive)
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
