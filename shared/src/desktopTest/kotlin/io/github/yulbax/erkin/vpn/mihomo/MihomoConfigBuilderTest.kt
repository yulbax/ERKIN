package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MihomoConfigBuilderTest {
    private val vless = ProxyProtocol.VLESS.engineProxy(
        "p1",
        """{"type":"vless","server":"a.example","port":443,"udp":true,"uuid":"u","tls":true,"servername":"a.example","client-fingerprint":"chrome"}"""
    )

    @Test
    fun androidRoutesByeDpiAppsByPackageAndEverythingElseToTheServer() {
        val config = build(platform = MihomoPlatform.Android)
        assertFalse(config.containsKey("tun"))
        assertEquals(
            listOf("IN-NAME,probe-in,p1", "PROCESS-NAME,org.dpi,byedpi", "MATCH,p1"),
            config.rules()
        )
        assertEquals(listOf("p1", "byedpi"), config.getValue("proxies").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        assertNull(config.getValue("dns").jsonObject["direct-nameserver"])
    }

    @Test
    fun androidWithoutServersSendsUnmatchedTrafficToByeDpi() {
        val config = Json.parseToJsonElement(
            MihomoConfigBuilder.build(emptyList(), "", listOf("org.dpi"), emptyList(), 1081, 2080, "u", "p")
        ).jsonObject
        assertEquals(listOf("IN-NAME,probe-in,DIRECT", "PROCESS-NAME,org.dpi,byedpi", "MATCH,byedpi"), config.rules())
        assertEquals("tls://1.1.1.1", config.getValue("dns").jsonObject.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun desktopOwnsTheTunAndRoutesEachProcessToItsChannel() {
        val config = build(
            platform = MihomoPlatform.Desktop(listOf("ciadpi"), SystemDns.Server("192.168.1.1"), byeDpiProbePort = 3080),
            byeDpi = listOf("discord.exe", "C:\\Games\\game.exe"),
            vpn = listOf("telegram.exe"),
            directPaths = listOf("C:\\venv\\python.exe")
        )
        val tun = config.getValue("tun").jsonObject
        assertEquals(MihomoConfigBuilder.DESKTOP_INTERFACE_NAME, tun.getValue("device").jsonPrimitive.content)
        assertTrue(tun.getValue("strict-route").jsonPrimitive.boolean)
        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "IN-NAME,byedpi-probe-in,byedpi",
                "PROCESS-NAME,ciadpi,DIRECT",
                "PROCESS-PATH,C:\\Games\\game.exe,byedpi",
                "PROCESS-PATH,C:\\venv\\python.exe,DIRECT",
                "PROCESS-NAME,discord.exe,byedpi",
                "PROCESS-NAME,telegram.exe,p1",
                "MATCH,DIRECT"
            ),
            config.rules()
        )
        val dns = config.getValue("dns").jsonObject
        assertEquals("fake-ip", dns.getValue("enhanced-mode").jsonPrimitive.content)
        assertEquals("192.168.1.1", dns.getValue("direct-nameserver").jsonArray.single().jsonPrimitive.content)
        assertEquals("tls://1.1.1.1#p1", dns.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
        val probe = config.getValue("listeners").jsonArray.map { it.jsonObject }.single { it["name"]?.jsonPrimitive?.content == "byedpi-probe-in" }
        assertEquals(3080, probe.getValue("port").jsonPrimitive.content.toInt())
        assertFalse(probe.containsKey("users"))
    }

    @Test
    fun desktopUsesTheSystemResolverWhenItIsSafe() {
        val config = build(platform = MihomoPlatform.Desktop(emptyList(), SystemDns.Local))
        assertEquals("system://", config.getValue("dns").jsonObject.getValue("direct-nameserver").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun ipv6FollowsTheSettingUnlessTheServerIsIpv4Only() {
        val enabled = NetworkOptions(ipv6Mode = Ipv6Mode.ENABLE)
        assertTrue(build(options = enabled).getValue("ipv6").jsonPrimitive.boolean)
        val awg = requireNotNull(LinkParser.parse(AWG_IPV4_ONLY))
        val config = Json.parseToJsonElement(
            MihomoConfigBuilder.build(
                listOf(ProxyProtocol.AMNEZIAWG.engineProxy("p2", awg.proxyJson())), "p2",
                emptyList(), emptyList(), 1081, 2080, "u", "p", enabled
            )
        ).jsonObject
        assertFalse(config.getValue("ipv6").jsonPrimitive.boolean)
    }

    @Test
    fun amneziaWg3OptionsAreCarriedOver() {
        val awg = requireNotNull(LinkParser.parse(AWG_IPV4_ONLY))
        val proxy = requireNotNull(MihomoProxy.of(ProxyProtocol.AMNEZIAWG.engineProxy("p2", awg.proxyJson()), null))
        assertEquals("wireguard", proxy.getValue("type").jsonPrimitive.content)
        val options = proxy.getValue("amnezia-wg-option").jsonObject
        assertEquals(3, options.getValue("version").jsonPrimitive.content.toInt())
        assertEquals("100-200", options.getValue("h1").jsonPrimitive.content)
        assertTrue(options.getValue("random-trailers").jsonPrimitive.boolean)
        assertEquals("aHeaderKey=", options.getValue("header-protection-key").jsonPrimitive.content)
    }

    @Test
    fun preferredFingerprintReplacesTheLinkOneOnTlsProxies() {
        val proxy = requireNotNull(MihomoProxy.of(vless, TlsFingerprint.FIREFOX))
        assertEquals("p1", proxy.getValue("name").jsonPrimitive.content)
        assertEquals(TlsFingerprint.FIREFOX.wire, proxy.getValue("client-fingerprint").jsonPrimitive.content)
        val awg = requireNotNull(LinkParser.parse(AWG_IPV4_ONLY))
        val wireguard = requireNotNull(MihomoProxy.of(ProxyProtocol.AMNEZIAWG.engineProxy("p2", awg.proxyJson()), TlsFingerprint.FIREFOX))
        assertNull(wireguard["client-fingerprint"])
    }

    @Test
    fun xhttpLinksBecomeXhttpOptions() {
        val parsed = requireNotNull(
            LinkParser.parse("vless://id@x.example:443?security=tls&sni=x.example&type=xhttp&path=%2Fxh&mode=packet-up#X")
        )
        val proxy = requireNotNull(MihomoProxy.of(ProxyProtocol.VLESS.engineProxy("p3", parsed.proxyJson()), null))
        assertEquals("xhttp", proxy.getValue("network").jsonPrimitive.content)
        val xhttp = proxy.getValue("xhttp-opts").jsonObject
        assertEquals("/xh", xhttp.getValue("path").jsonPrimitive.content)
        assertEquals("packet-up", xhttp.getValue("mode").jsonPrimitive.content)
    }

    private fun build(
        platform: MihomoPlatform = MihomoPlatform.Android,
        byeDpi: List<String> = listOf("org.dpi"),
        vpn: List<String> = emptyList(),
        directPaths: List<String> = emptyList(),
        options: NetworkOptions = NetworkOptions()
    ): JsonObject = Json.parseToJsonElement(
        MihomoConfigBuilder.build(listOf(vless), "p1", byeDpi, vpn, 1081, 2080, "u", "p", options, platform, directPaths)
    ).jsonObject

    private fun JsonObject.rules(): List<String> = getValue("rules").jsonArray.map { it.jsonPrimitive.content }

    private companion object {
        val AWG_IPV4_ONLY = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = aPrivateKey=
            Jc = 4
            H1 = 100-200
            HeaderProtectionKey = aHeaderKey=
            RandomTrailers = on

            [Peer]
            PublicKey = aPublicKey=
            AllowedIPs = 0.0.0.0/0
            Endpoint = vpn.example.com:51820
        """.trimIndent()
    }
}
