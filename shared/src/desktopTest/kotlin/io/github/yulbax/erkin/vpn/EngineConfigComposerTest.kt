package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
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
