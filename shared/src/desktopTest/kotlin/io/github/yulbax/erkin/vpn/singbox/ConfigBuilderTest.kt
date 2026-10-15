package io.github.yulbax.erkin.vpn.singbox

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import io.github.yulbax.erkin.vpn.core.TunStack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderTest {
    private val proxies = listOf(
        ProxyProtocol.VLESS.engineProxy("p1", """{"type":"vless","server":"a.example","server_port":443,"uuid":"u","tls":{"enabled":true,"server_name":"a.example"}}"""),
        ProxyProtocol.TROJAN.engineProxy("p2", """{"type":"trojan","server":"b.example","server_port":443,"password":"x","tls":{"enabled":true,"utls":{"enabled":true,"fingerprint":"firefox"}}}""")
    )

    @Test
    fun androidDefaultConfigIsUnchanged() {
        assertEquals(
            golden("android-default.json"),
            ConfigBuilder.build(proxies, "p2", listOf("org.telegram"), listOf("com.vpn.app"), listOf("org.telegram", "com.vpn.app"), 1081, 2080, "user", "pass")
        )
    }

    @Test
    fun androidCustomConfigIsUnchanged() {
        assertEquals(
            golden("android-custom.json"),
            ConfigBuilder.build(
                proxies, "p1", emptyList(), listOf("com.vpn.app"), listOf("com.vpn.app"), 1081, 2080, "user", "pass",
                NetworkOptions(TunStack.SYSTEM, 1500, Ipv6Mode.PREFER, "9.9.9.9", "8.8.8.8", sniff = false, bypassLan = true, preferredFingerprint = TlsFingerprint.CHROME)
            )
        )
    }

    @Test
    fun healthGroupHoldsOnlyTheActiveServer() {
        val outbounds = Json.parseToJsonElement(
            ConfigBuilder.build(proxies, "p2", emptyList(), listOf("com.vpn.app"), listOf("com.vpn.app"), 1081, 2080, "user", "pass")
        ).jsonObject.getValue("outbounds").jsonArray.map { it.jsonObject }
        val health = outbounds.single { it["tag"]?.jsonPrimitive?.content == ConfigBuilder.HEALTH_GROUP_TAG }
        assertEquals("selector", health.getValue("type").jsonPrimitive.content)
        assertEquals(setOf("p2"), health.getValue("outbounds").jsonArray.map { it.jsonPrimitive.content }.toSet())
    }

    @Test
    fun noHealthGroupWithoutServers() {
        val outbounds = Json.parseToJsonElement(
            ConfigBuilder.build(emptyList(), "", listOf("org.telegram"), emptyList(), listOf("org.telegram"), 1081, 2080, "user", "pass")
        ).jsonObject.getValue("outbounds").jsonArray.map { it.jsonObject }
        assertFalse(outbounds.any { it["tag"]?.jsonPrimitive?.content == ConfigBuilder.HEALTH_GROUP_TAG })
    }

    @Test
    fun latencyTestConfigHoldsEveryServerAndNothingThatListens() {
        val config = Json.parseToJsonElement(ConfigBuilder.buildLatencyTest(proxies)).jsonObject
        assertFalse(config.containsKey("inbounds"))
        assertEquals(
            proxies.map { it.tag },
            config.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        )
        val dns = config.getValue("dns").jsonObject.getValue("servers").jsonArray.single().jsonObject
        assertEquals("local", dns.getValue("tag").jsonPrimitive.content)
        assertEquals("local", config.getValue("route").jsonObject.getValue("default_domain_resolver").jsonPrimitive.content)
    }

    @Test
    fun desktopConfigRoutesByProcessAndDefaultsToDirect() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies, "p1", listOf("discord.exe"), listOf("telegram.exe"), listOf("discord.exe", "telegram.exe"),
                1081, 2080, "user", "pass",
                routing = AppRouting.DesktopProcesses(listOf("ciadpi.exe"))
            )
        ).jsonObject

        val route = config.getValue("route").jsonObject
        val rules = route.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals("direct", route.getValue("final").jsonPrimitive.content)
        assertFalse(rules.any { it["action"]?.jsonPrimitive?.content == "reject" })
        assertEquals(listOf("ciadpi.exe"), rules.processRule("direct"))
        assertEquals(listOf("discord.exe"), rules.processRule("byedpi"))
        assertEquals(listOf("telegram.exe"), rules.processRule("p1"))
        assertTrue(rules.none { it.containsKey("package_name") })

        val tun = config.getValue("inbounds").jsonArray.first().jsonObject
        assertFalse(tun.containsKey("include_package"))
        assertEquals("true", tun.getValue("strict_route").jsonPrimitive.content)
        assertEquals(ConfigBuilder.DESKTOP_INTERFACE_NAME, tun.getValue("interface_name").jsonPrimitive.content)
    }

    @Test
    fun desktopAnswersNamesWithFakeIpAndResolvesEachChannelItsOwnWay() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies, "p1", listOf("discord.exe"), listOf("telegram.exe"), listOf("discord.exe", "telegram.exe"),
                1081, 2080, "user", "pass",
                routing = AppRouting.DesktopProcesses(listOf("ciadpi.exe"), SystemDns.Server("192.168.1.1"), byeDpiProbePort = 3080)
            )
        ).jsonObject

        val dns = config.getValue("dns").jsonObject
        val servers = dns.getValue("servers").jsonArray.map { it.jsonObject }.associateBy { it.getValue("tag").jsonPrimitive.content }
        assertEquals("fakeip", servers.getValue("fakeip").getValue("type").jsonPrimitive.content)
        assertEquals("192.168.1.1", servers.getValue("system").getValue("server").jsonPrimitive.content)
        val dnsRules = dns.getValue("rules").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "system", "fakeip"), dnsRules.map { it.getValue("server").jsonPrimitive.content })
        assertEquals("1", dnsRules.last().getValue("rewrite_ttl").jsonPrimitive.content)
        assertTrue(dnsRules.none { it.containsKey("process_name") })

        val direct = config.getValue("outbounds").jsonArray.map { it.jsonObject }.single { it["tag"]?.jsonPrimitive?.content == "direct" }
        assertEquals("system", direct.getValue("domain_resolver").jsonPrimitive.content)

        val rules = config.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val byeDpiResolve = rules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == "resolve" && it["process_name"]?.jsonArray?.map { name -> name.jsonPrimitive.content } == listOf("discord.exe")
        }
        val byeDpiRoute = rules.indexOfFirst {
            it["action"]?.jsonPrimitive?.content == "route" && it["outbound"]?.jsonPrimitive?.content == "byedpi" && it.containsKey("process_name")
        }
        assertTrue(byeDpiResolve in 0 until byeDpiRoute)
        assertEquals("byedpi-dns", rules[byeDpiResolve].getValue("server").jsonPrimitive.content)

        val probe = config.getValue("inbounds").jsonArray.map { it.jsonObject }.single { it["tag"]?.jsonPrimitive?.content == "byedpi-probe-in" }
        assertEquals(3080, probe.getValue("listen_port").jsonPrimitive.content.toInt())
        assertFalse(probe.containsKey("users"))
        val probeRoute = rules.single { it["inbound"]?.jsonArray?.single()?.jsonPrimitive?.content == "byedpi-probe-in" && it["action"]?.jsonPrimitive?.content == "route" }
        assertEquals("byedpi", probeRoute.getValue("outbound").jsonPrimitive.content)

        val cache = config.getValue("experimental").jsonObject.getValue("cache_file").jsonObject
        assertEquals("true", cache.getValue("store_fakeip").jsonPrimitive.content)
    }

    @Test
    fun desktopWithoutAKnownSystemResolverFallsBackToTheDirectDnsSetting() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies, "p1", emptyList(), listOf("telegram.exe"), listOf("telegram.exe"),
                1081, 2080, "user", "pass",
                routing = AppRouting.DesktopProcesses(emptyList(), SystemDns.Unknown)
            )
        ).jsonObject
        val tags = config.getValue("dns").jsonObject.getValue("servers").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        assertFalse("system" in tags)
        val direct = config.getValue("outbounds").jsonArray.map { it.jsonObject }.single { it["tag"]?.jsonPrimitive?.content == "direct" }
        assertEquals("local", direct.getValue("domain_resolver").jsonPrimitive.content)
        assertFalse(config.getValue("inbounds").jsonArray.any { it.jsonObject["tag"]?.jsonPrimitive?.content == "byedpi-probe-in" })
    }

    @Test
    fun desktopUnassignedProcessesLeaveDirectly() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies, "p1", emptyList(), listOf("telegram.exe"), listOf("telegram.exe"), 1081, 2080, "user", "pass",
                routing = AppRouting.DesktopProcesses(listOf("ciadpi.exe"))
            )
        ).jsonObject
        val route = config.getValue("route").jsonObject
        assertEquals(listOf("ciadpi.exe"), route.getValue("rules").jsonArray.map { it.jsonObject }.processRule("direct"))
        assertEquals("direct", route.getValue("final").jsonPrimitive.content)
    }

    @Test
    fun preferredFingerprintSkipsQuicBasedHysteria2() {
        val hysteria = requireNotNull(LinkParser.parse("hysteria2://secret@hy.example.net:8443?sni=hy.example.net#H"))
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies = proxies + ProxyProtocol.HYSTERIA2.engineProxy("p3", hysteria.outboundJson()),
                activeProxyTag = "p3",
                byeDpiPackages = emptyList(),
                vpnPackages = listOf("org.telegram"),
                tunneledPackages = listOf("org.telegram"),
                byeDpiPort = 1081,
                probePort = 2080,
                probeUser = "user",
                probePass = "pass",
                options = NetworkOptions(preferredFingerprint = TlsFingerprint.CHROME)
            )
        ).jsonObject

        val outbounds = config.getValue("outbounds").jsonArray.map { it.jsonObject }
        fun tlsOf(tag: String) = outbounds.single { it["tag"]?.jsonPrimitive?.content == tag }.getValue("tls").jsonObject
        assertFalse(tlsOf("p3").containsKey("utls"))
        assertEquals("chrome", tlsOf("p1").getValue("utls").jsonObject.getValue("fingerprint").jsonPrimitive.content)
    }

    @Test
    fun amneziaWgProfileIsEmittedAsEndpoint() {
        val awg = requireNotNull(LinkParser.parse(AWG_CONFIG))
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies = proxies + ProxyProtocol.AMNEZIAWG.engineProxy("p3", awg.outboundJson()),
                activeProxyTag = "p3",
                byeDpiPackages = emptyList(),
                vpnPackages = listOf("org.telegram"),
                tunneledPackages = listOf("org.telegram"),
                byeDpiPort = 1081,
                probePort = 2080,
                probeUser = "user",
                probePass = "pass"
            )
        ).jsonObject

        val endpoint = config.getValue("endpoints").jsonArray.single().jsonObject
        assertEquals("awg", endpoint.getValue("type").jsonPrimitive.content)
        assertEquals("p3", endpoint.getValue("tag").jsonPrimitive.content)

        val outbounds = config.getValue("outbounds").jsonArray.map { it.jsonObject }
        assertTrue(outbounds.none { it["type"]?.jsonPrimitive?.content == "awg" })

        assertTrue(outbounds.none { it["type"]?.jsonPrimitive?.content == "selector" && it["tag"]?.jsonPrimitive?.content != ConfigBuilder.HEALTH_GROUP_TAG })
        val remoteDns = config.getValue("dns").jsonObject.getValue("servers").jsonArray.first().jsonObject
        assertEquals("p3", remoteDns.getValue("detour").jsonPrimitive.content)

        assertEquals("ipv4_only", config.getValue("dns").jsonObject.getValue("strategy").jsonPrimitive.content)

        val rules = config.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val resolve = rules.single { it["action"]?.jsonPrimitive?.content == "resolve" }
        assertEquals("remote", resolve.getValue("server").jsonPrimitive.content)
        assertEquals("ipv4_only", resolve.getValue("strategy").jsonPrimitive.content)
        assertTrue(
            rules.indexOf(resolve) <
                rules.indexOfFirst { it["inbound"] != null }
        )
    }

    @Test
    fun keepsDomainsUnresolvedForRegularProxies() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies = proxies,
                activeProxyTag = "p1",
                byeDpiPackages = emptyList(),
                vpnPackages = listOf("org.telegram"),
                tunneledPackages = listOf("org.telegram"),
                byeDpiPort = 1081,
                probePort = 2080,
                probeUser = "user",
                probePass = "pass"
            )
        ).jsonObject

        val rules = config.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        assertTrue(rules.none { it["action"]?.jsonPrimitive?.content == "resolve" })
    }

    private fun List<JsonObject>.processRule(outbound: String): List<String>? =
        firstOrNull { it["outbound"]?.jsonPrimitive?.content == outbound && it.containsKey("process_name") }
            ?.let { rule -> (rule.getValue("process_name") as JsonArray).map { it.jsonPrimitive.content } }

    private fun golden(name: String): String =
        requireNotNull(javaClass.getResource("/golden/$name")).readText()
    @Test
    fun desktopPathRulesComeBeforeNameRulesAndCanForceDirect() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                proxies, "p1",
                byeDpiPackages = listOf("C:\\Tools\\curl.exe"),
                vpnPackages = listOf("python.exe", "curl.exe"),
                tunneledPackages = emptyList(),
                byeDpiPort = 1081, probePort = 2080, probeUser = "user", probePass = "pass",
                routing = AppRouting.DesktopProcesses(listOf("ciadpi.exe")),
                directPaths = listOf("C:\\venv\\python.exe")
            )
        ).jsonObject
        val rules = config.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val keys = rules.filter { it["action"]?.jsonPrimitive?.content == "route" }.mapNotNull { rule ->
            when {
                rule.containsKey("process_path") -> "path:" + rule.getValue("outbound").jsonPrimitive.content
                rule.containsKey("process_name") -> "name:" + rule.getValue("outbound").jsonPrimitive.content
                else -> null
            }
        }
        assertEquals(listOf("name:direct", "path:byedpi", "path:direct", "name:p1"), keys)
        val pathDirect = rules.first { it.containsKey("process_path") && it["outbound"]?.jsonPrimitive?.content == "direct" }
        assertEquals("C:\\venv\\python.exe", pathDirect.getValue("process_path").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun withoutServersEverythingLeavesThroughDirectOrByeDpi() {
        val config = Json.parseToJsonElement(
            ConfigBuilder.build(
                emptyList(), "",
                byeDpiPackages = listOf("org.dpi"), vpnPackages = emptyList(), tunneledPackages = listOf("org.dpi"),
                byeDpiPort = 1081, probePort = 2080, probeUser = "user", probePass = "pass"
            )
        ).jsonObject
        val outbounds = config.getValue("outbounds").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        assertEquals(listOf("byedpi", "direct"), outbounds)
        val route = config.getValue("route").jsonObject
        assertEquals("direct", route.getValue("final").jsonPrimitive.content)
        val remoteDns = config.getValue("dns").jsonObject.getValue("servers").jsonArray.first().jsonObject
        assertFalse(remoteDns.containsKey("detour"))
        val probe = route.getValue("rules").jsonArray.map { it.jsonObject }.first { it.containsKey("inbound") }
        assertEquals("direct", probe.getValue("outbound").jsonPrimitive.content)
    }

    private companion object {
        val AWG_CONFIG = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = aPrivateKey=
            Jc = 4
            Jmin = 50
            Jmax = 1000
            S1 = 15
            S2 = 36
            H1 = 1148364954
            H2 = 1813799810
            H3 = 1082378570
            H4 = 1151590459

            [Peer]
            PublicKey = aPublicKey=
            AllowedIPs = 0.0.0.0/0
            Endpoint = vpn.example.com:51820
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
