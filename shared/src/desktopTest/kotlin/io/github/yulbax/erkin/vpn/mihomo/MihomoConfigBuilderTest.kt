package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
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
    fun androidRoutesOnlyKnownAppsAndRejectsEverythingElseInTheTun() {
        val config = build(platform = MihomoPlatform.Android(), vpn = listOf("org.vpn"))
        assertFalse(config.containsKey("tun"))
        assertEquals(
            listOf("IN-NAME,probe-in,p1", "PROCESS-NAME,org.dpi,byedpi", "PROCESS-NAME,org.vpn,p1", "MATCH,REJECT"),
            config.rules()
        )
        assertEquals(listOf("p1", "byedpi"), config.getValue("proxies").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
        assertEquals("system://", config.getValue("dns").jsonObject.getValue("direct-nameserver").jsonArray.single().jsonPrimitive.content)
        assertEquals("system://", config.getValue("dns").jsonObject.getValue("nameserver-policy").jsonObject.values.single().jsonPrimitive.content)
    }

    @Test
    fun androidWithoutServersStillRejectsUnmatchedTraffic() {
        val config = Json.parseToJsonElement(
            MihomoConfigBuilder.build(engine(emptyList(), "", listOf("org.dpi"), emptyList()), PROBE)
        ).jsonObject
        assertEquals(listOf("IN-NAME,probe-in,DIRECT", "PROCESS-NAME,org.dpi,byedpi", "MATCH,REJECT"), config.rules())
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
    fun userRulesOverrideAppChannelsButNotTheServiceRules() {
        val rules = listOf(
            RoutingRule(RuleMatch.DOMAIN_SUFFIX, "ru", RuleTarget.DIRECT),
            RoutingRule(RuleMatch.DOMAIN_KEYWORD, "ads", RuleTarget.BLOCK),
            RoutingRule(RuleMatch.IP_CIDR, "1.1.1.1/32", RuleTarget.BYEDPI),
            RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN)
        )
        val config = build(vpn = listOf("org.vpn"), options = NetworkOptions(bypassLan = true), rules = rules)
        val userRules = listOf(
            "DOMAIN-SUFFIX,ru,DIRECT",
            "DOMAIN-KEYWORD,ads,REJECT",
            "IP-CIDR,1.1.1.1/32,byedpi",
            "DOMAIN,a.example,p1"
        )
        val all = config.rules()
        assertEquals("IN-NAME,probe-in,p1", all.first())
        assertEquals(userRules, all.dropWhile { !it.startsWith("DOMAIN-SUFFIX") }.take(userRules.size))
        assertTrue(all.indexOf(userRules.first()) > all.indexOfLast { it.startsWith("IP-CIDR") && it.endsWith("no-resolve") })
        assertTrue(all.indexOf(userRules.last()) < all.indexOf("PROCESS-NAME,org.dpi,byedpi"))
        val noServer = Json.parseToJsonElement(
            MihomoConfigBuilder.build(engine(emptyList(), "", listOf("org.dpi"), emptyList(), rules = rules), PROBE)
        ).jsonObject
        assertTrue("DOMAIN,a.example,REJECT" in noServer.rules())
    }

    @Test
    fun ruleListsAndCountriesBecomeLocalProvidersInTheRulesPosition() {
        val url = "https://example.com/ads.txt"
        val countryUrl = RuleLists.country("RU").url
        val country = RoutingRule(RuleMatch.RULE_SET, countryUrl, RuleTarget.DIRECT)
        val rules = listOf(
            RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN),
            RoutingRule(RuleMatch.RULE_SET, url, RuleTarget.BLOCK),
            country
        )
        val engine = engine(listOf(vless), "p1", listOf("org.dpi"), emptyList(), rules = rules).copy(
            ruleLists = mapOf(url to setOf(RuleSetKind.DOMAIN, RuleSetKind.IPCIDR), countryUrl to setOf(RuleSetKind.IPCIDR))
        )
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE)).jsonObject
        val domains = RuleLists.providerName(url, RuleSetKind.DOMAIN)
        val cidrs = RuleLists.providerName(url, RuleSetKind.IPCIDR)
        val countryCidrs = RuleLists.providerName(countryUrl, RuleSetKind.IPCIDR)

        val all = config.rules()
        val first = all.indexOf("DOMAIN,a.example,p1")
        assertEquals(
            listOf("RULE-SET,$domains,REJECT", "RULE-SET,$cidrs,REJECT", "RULE-SET,$countryCidrs,DIRECT"),
            all.subList(first + 1, first + 4)
        )
        assertFalse(all.any { it.startsWith("GEOIP") })
        val provider = config.getValue("rule-providers").jsonObject.getValue(domains).jsonObject
        assertEquals("file", provider.getValue("type").jsonPrimitive.content)
        assertEquals("domain", provider.getValue("behavior").jsonPrimitive.content)
        assertEquals("mrs", provider.getValue("format").jsonPrimitive.content)
        assertEquals(RuleLists.path(url, RuleSetKind.DOMAIN), provider.getValue("path").jsonPrimitive.content)
        assertFalse("rule-providers" in build(rules = rules.take(1)))
    }

    @Test
    fun androidResolvesDirectTrafficWithThePhonesPrivateDns() {
        val config = build(platform = MihomoPlatform.Android(SystemDns.Tls("dns.google")))
        assertEquals("tls://dns.google", config.getValue("dns").jsonObject.getValue("direct-nameserver").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun snifferOnlyFeedsRuleMatchingAndFollowsTheSetting() {
        val sniffer = build().getValue("sniffer").jsonObject
        assertTrue(sniffer.getValue("enable").jsonPrimitive.boolean)
        assertFalse(sniffer.getValue("override-destination").jsonPrimitive.boolean)
        assertTrue(sniffer.getValue("parse-pure-ip").jsonPrimitive.boolean)
        assertEquals(setOf("TLS", "HTTP", "QUIC"), sniffer.getValue("sniff").jsonObject.keys)
        assertFalse(build(options = NetworkOptions(sniff = false)).containsKey("sniffer"))
    }

    @Test
    fun remoteDnsKeepsItsProtocolAndGoesThroughTheServer() {
        val doh = build(options = NetworkOptions(dnsRemote = "https://dns.google/dns-query"))
        assertEquals("https://dns.google/dns-query#p1", doh.getValue("dns").jsonObject.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
        val broken = build(options = NetworkOptions(dnsRemote = "system://"))
        assertEquals("tls://1.1.1.1#p1", broken.getValue("dns").jsonObject.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun blockingQuicRejectsUdp443BeforeAppChannels() {
        val rule = "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT"
        assertFalse(rule in build().rules())
        val rules = build(vpn = listOf("org.vpn"), options = NetworkOptions(blockQuic = true)).rules()
        assertTrue(rules.indexOf(rule) in 1 until rules.indexOf("PROCESS-NAME,org.vpn,p1"))
    }

    @Test
    fun everyHopOfAChainIsEmittedWithItsDialerProxy() {
        val entry = vless.copy(tag = "p2")
        val exit = vless.copy(tag = "p9", dialerProxy = "p2")
        val config = Json.parseToJsonElement(
            MihomoConfigBuilder.build(engine(listOf(entry, exit), "p9", listOf("org.dpi"), listOf("org.vpn")), PROBE)
        ).jsonObject
        val proxies = config.getValue("proxies").jsonArray.map { it.jsonObject }
        assertEquals(listOf("p2", "p9", "byedpi"), proxies.map { it.getValue("name").jsonPrimitive.content })
        assertEquals("p2", proxies[1].getValue("dialer-proxy").jsonPrimitive.content)
        assertFalse(proxies[0].containsKey("dialer-proxy"))
        assertTrue("PROCESS-NAME,org.vpn,p9" in config.rules())
    }

    @Test
    fun desktopUsesTheSystemResolverWhenItIsSafe() {
        val config = build(platform = MihomoPlatform.Desktop(emptyList(), SystemDns.Local))
        assertEquals("system://", config.getValue("dns").jsonObject.getValue("direct-nameserver").jsonArray.single().jsonPrimitive.content)
        assertEquals("system://", config.getValue("dns").jsonObject.getValue("nameserver-policy").jsonObject.values.single().jsonPrimitive.content)
    }

    @Test
    fun ipv6FollowsTheSettingUnlessTheServerIsIpv4Only() {
        val enabled = NetworkOptions(ipv6Mode = Ipv6Mode.ENABLE)
        assertTrue(build(options = enabled).getValue("ipv6").jsonPrimitive.boolean)
        val awg = requireNotNull(LinkParser.parse(AWG_IPV4_ONLY))
        val config = Json.parseToJsonElement(
            MihomoConfigBuilder.build(
                engine(listOf(ProxyProtocol.AMNEZIAWG.engineProxy("p2", awg.proxyJson())), "p2", emptyList(), emptyList(), enabled),
                PROBE
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
        platform: MihomoPlatform = MihomoPlatform.Android(),
        byeDpi: List<String> = listOf("org.dpi"),
        vpn: List<String> = emptyList(),
        directPaths: List<String> = emptyList(),
        options: NetworkOptions = NetworkOptions(),
        rules: List<RoutingRule> = emptyList()
    ): JsonObject = Json.parseToJsonElement(
        MihomoConfigBuilder.build(engine(listOf(vless), "p1", byeDpi, vpn, options, directPaths, rules), PROBE, platform)
    ).jsonObject

    private fun engine(
        proxies: List<EngineProxy>,
        activeTag: String,
        byeDpi: List<String>,
        vpn: List<String>,
        options: NetworkOptions = NetworkOptions(),
        directPaths: List<String> = emptyList(),
        rules: List<RoutingRule> = emptyList()
    ) = EngineConfig(proxies, activeTag, byeDpi, vpn, byeDpi + vpn, 1081, options, directPaths, rules)

    private fun JsonObject.rules(): List<String> = getValue("rules").jsonArray.map { it.jsonPrimitive.content }

    private companion object {
        val PROBE = ProbeInbound(2080, "u", "p")
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
