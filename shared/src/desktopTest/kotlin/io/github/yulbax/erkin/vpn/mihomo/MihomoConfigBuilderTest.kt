package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.AppliedSubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionProvider
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.vpn.core.EngineBalancer
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
import kotlinx.serialization.json.JsonPrimitive
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
    fun androidSendsTunneledDirectAppsDirectAfterUserRules() {
        val engine = engine(
            listOf(vless), "p1", listOf("org.dpi"), listOf("org.vpn"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.VPN))
        ).copy(directPackages = listOf("org.direct"))
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject
        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "DOMAIN-SUFFIX,example.org,p1",
                "PROCESS-NAME,org.dpi,byedpi",
                "PROCESS-NAME,org.vpn,p1",
                "PROCESS-NAME,org.direct,DIRECT",
                "MATCH,REJECT"
            ),
            config.rules()
        )
    }

    @Test
    fun aRuleBoundToAnotherServerIsRoutedToItAndAnUnknownServerFallsBackToTheMain() {
        val other = vless.copy(tag = "p2")
        val engine = engine(
            listOf(vless, other), "p1", emptyList(), emptyList(),
            rules = listOf(
                RoutingRule(RuleMatch.DOMAIN, "a.example", RuleTarget.VPN, serverId = 2),
                RoutingRule(RuleMatch.DOMAIN, "b.example", RuleTarget.VPN, serverId = 9),
                RoutingRule(RuleMatch.DOMAIN, "c.example", RuleTarget.VPN)
            )
        ).copy(serverTags = mapOf(2L to "p2"))
        val rules = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject.rules()

        assertTrue("DOMAIN,a.example,p2" in rules)
        assertTrue("DOMAIN,b.example,p1" in rules)
        assertTrue("DOMAIN,c.example,p1" in rules)
    }

    @Test
    fun blockedAppsAreRejectedBeforeUserRules() {
        val engine = engine(
            listOf(vless), "p1", emptyList(), listOf("org.vpn"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.DIRECT))
        ).copy(blockedPackages = listOf("org.blocked"))
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject
        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "PROCESS-NAME,org.blocked,REJECT",
                "DOMAIN-SUFFIX,example.org,DIRECT",
                "PROCESS-NAME,org.vpn,p1",
                "MATCH,REJECT"
            ),
            config.rules()
        )
    }

    @Test
    fun appRulesFollowTheQuicBlockAndKeepTheirPlaceInTheList() {
        val rules = listOf(
            RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.DIRECT),
            RoutingRule(RuleMatch.DOMAIN_SUFFIX, "youtube.com", RuleTarget.VPN, app = "org.vpn"),
            RoutingRule(RuleMatch.DOMAIN_SUFFIX, "ru", RuleTarget.DIRECT)
        )
        val config = build(byeDpi = emptyList(), vpn = listOf("org.vpn"), options = NetworkOptions(blockQuic = true), rules = rules)

        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "AND,((NETWORK,UDP),(DST-PORT,443)),REJECT",
                "DOMAIN-SUFFIX,example.org,DIRECT",
                "AND,((PROCESS-NAME,org.vpn),(DOMAIN-SUFFIX,youtube.com)),p1",
                "DOMAIN-SUFFIX,ru,DIRECT",
                "PROCESS-NAME,org.vpn,p1",
                "MATCH,REJECT"
            ),
            config.rules()
        )
    }

    @Test
    fun onlyItsOwnRulesOpenABlockedAppAndOtherRulesKeepTheListOrder() {
        val engine = engine(
            listOf(vless), "p1", emptyList(), listOf("org.vpn"),
            rules = listOf(
                RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.DIRECT),
                RoutingRule(RuleMatch.DOMAIN_SUFFIX, "game.io", RuleTarget.DIRECT, app = "org.blocked"),
                RoutingRule(RuleMatch.IP_CIDR, "1.2.3.4/32", RuleTarget.VPN, app = "C:\\Games\\game.exe")
            )
        ).copy(blockedPackages = listOf("org.blocked"))
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject
        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "AND,((PROCESS-NAME,org.blocked),(DOMAIN-SUFFIX,game.io)),DIRECT",
                "PROCESS-NAME,org.blocked,REJECT",
                "DOMAIN-SUFFIX,example.org,DIRECT",
                "AND,((PROCESS-PATH,C:\\Games\\game.exe),(IP-CIDR,1.2.3.4/32)),p1",
                "PROCESS-NAME,org.vpn,p1",
                "MATCH,REJECT"
            ),
            config.rules()
        )
    }

    @Test
    fun unlistedTrafficFollowsTheChannelForNewApps() {
        fun lastRule(newApps: ConnectionType, platform: MihomoPlatform, active: String = "p1") =
            Json.parseToJsonElement(
                MihomoConfigBuilder.build(engine(listOf(vless), active, emptyList(), emptyList()).copy(newApps = newApps), PROBE, platform)
            ).jsonObject.rules().last()
        val linux = MihomoPlatform.Desktop(DesktopOs.LINUX, emptyList())

        assertEquals("MATCH,p1", lastRule(ConnectionType.VPN, linux))
        assertEquals("MATCH,p1", lastRule(ConnectionType.VPN, MihomoPlatform.Android()))
        assertEquals("MATCH,byedpi", lastRule(ConnectionType.BYEDPI, linux))
        assertEquals("MATCH,DIRECT", lastRule(ConnectionType.DIRECT, MihomoPlatform.Android()))
        assertEquals("MATCH,DIRECT", lastRule(ConnectionType.BYPASS, linux))
        assertEquals("MATCH,REJECT", lastRule(ConnectionType.BYPASS, MihomoPlatform.Android()))
    }

    @Test
    fun aDesktopBypassAppGoesDirectWhenNewAppsUseTheVpn() {
        val engine = engine(listOf(vless), "p1", emptyList(), emptyList())
            .copy(newApps = ConnectionType.VPN, bypassPackages = listOf("legacy-name"), directPaths = listOf("/usr/bin/bank"))
        val rules = Json.parseToJsonElement(
            MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Desktop(DesktopOs.WINDOWS, emptyList()))
        ).jsonObject.rules()

        assertEquals(listOf("IN-NAME,probe-in,p1", "PROCESS-PATH,/usr/bin/bank,DIRECT", "PROCESS-NAME,legacy-name,DIRECT", "MATCH,p1"), rules)
    }

    @Test
    fun linuxAlsoMatchesAProgramReplacedOnDiskWhileRunning() {
        val engine = engine(
            listOf(vless), "p1", emptyList(), listOf("/opt/zen/zen-bin"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.DIRECT, app = "/opt/zen/zen-bin"))
        ).copy(blockedPackages = listOf("/usr/bin/tracker"))
        val platform = MihomoPlatform.Desktop(DesktopOs.LINUX, listOf("ciadpi"))
        val rules = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, platform)).jsonObject.rules()

        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "PROCESS-NAME,ciadpi,DIRECT",
                "PROCESS-NAME,ciadpi (deleted),DIRECT",
                "PROCESS-PATH,/usr/bin/tracker,REJECT",
                "PROCESS-PATH,/usr/bin/tracker (deleted),REJECT",
                "AND,((OR,((PROCESS-PATH,/opt/zen/zen-bin),(PROCESS-PATH,/opt/zen/zen-bin (deleted)))),(DOMAIN-SUFFIX,example.org)),DIRECT",
                "PROCESS-PATH,/opt/zen/zen-bin,p1",
                "PROCESS-PATH,/opt/zen/zen-bin (deleted),p1",
                "MATCH,DIRECT"
            ),
            rules
        )
    }

    @Test
    fun windowsKeepsOneRulePerProgram() {
        val engine = engine(listOf(vless), "p1", emptyList(), listOf("C:\\Apps\\app.exe"))
        val platform = MihomoPlatform.Desktop(DesktopOs.WINDOWS, emptyList())
        val rules = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, platform)).jsonObject.rules()

        assertEquals(listOf("PROCESS-PATH,C:\\Apps\\app.exe,p1"), rules.filter { "app.exe" in it })
    }

    @Test
    fun blockedExecutablePathsAreMatchedByPath() {
        val engine = engine(listOf(vless), "p1", emptyList(), emptyList())
            .copy(blockedPackages = listOf("C:\\Games\\ads.exe", "tracker.exe"))
        val rules = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject.rules()

        assertTrue("PROCESS-PATH,C:\\Games\\ads.exe,REJECT" in rules)
        assertTrue("PROCESS-NAME,tracker.exe,REJECT" in rules)
    }

    @Test
    fun appsBoundToAnotherServerAreRoutedToIt() {
        val other = vless.copy(tag = "p2")
        val engine = engine(listOf(vless, other), "p1", emptyList(), listOf("org.main", "org.other"))
            .copy(vpnTargets = mapOf("org.other" to "p2"))
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject
        assertEquals(
            listOf("IN-NAME,probe-in,p1", "PROCESS-NAME,org.main,p1", "PROCESS-NAME,org.other,p2", "MATCH,REJECT"),
            config.rules()
        )
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
            platform = MihomoPlatform.Desktop(DesktopOs.WINDOWS, listOf("ciadpi"), SystemDns.Server("192.168.1.1"), byeDpiProbePort = 3080),
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
    fun aDesktopChecksByeDpiWhenOnlyARuleUsesIt() {
        val platform = MihomoPlatform.Desktop(DesktopOs.LINUX, emptyList(), byeDpiProbePort = 3080)
        val onlyRule = build(
            platform = platform,
            byeDpi = emptyList(),
            vpn = listOf("telegram"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "youtube.com", RuleTarget.BYEDPI))
        )
        val unused = build(platform = platform, byeDpi = emptyList(), vpn = listOf("telegram"))

        assertTrue("IN-NAME,byedpi-probe-in,byedpi" in onlyRule.rules())
        assertTrue(onlyRule.getValue("listeners").jsonArray.any { it.jsonObject["name"]?.jsonPrimitive?.content == "byedpi-probe-in" })
        assertFalse("IN-NAME,byedpi-probe-in,byedpi" in unused.rules())
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
    fun aListsDomainRuleComesBeforeItsAddressRule() {
        val countryUrl = RuleLists.country("RU").url
        val rules = listOf(RoutingRule(RuleMatch.RULE_SET, countryUrl, RuleTarget.DIRECT))
        val engine = engine(listOf(vless), "p1", listOf("org.dpi"), emptyList(), rules = rules)
            .copy(ruleLists = mapOf(countryUrl to setOf(RuleSetKind.IPCIDR, RuleSetKind.DOMAIN)))

        val listed = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE)).jsonObject.rules().filter { it.startsWith("RULE-SET") }

        assertEquals(
            listOf(
                "RULE-SET,${RuleLists.providerName(countryUrl, RuleSetKind.DOMAIN)},DIRECT",
                "RULE-SET,${RuleLists.providerName(countryUrl, RuleSetKind.IPCIDR)},DIRECT"
            ),
            listed
        )
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
        val config = build(platform = MihomoPlatform.Desktop(DesktopOs.WINDOWS, emptyList(), SystemDns.Local))
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

    @Test
    fun subscriptionRulesGoAfterUserRulesAndBeforeAppChannelsWithTheirProvidersPrefixed() {
        val provider = SubscriptionProvider(
            "voice",
            JsonObject(mapOf("type" to JsonPrimitive("http"), "behavior" to JsonPrimitive("domain"), "url" to JsonPrimitive("https://example.org/voice.mrs")))
        )
        val subscription = AppliedSubscriptionRules(
            7,
            SubscriptionRules(
                rules = listOf(
                    SubscriptionRule("DOMAIN-SUFFIX,openai.com", emptyList(), SubscriptionTarget.VPN),
                    SubscriptionRule("RULE-SET,voice", emptyList(), SubscriptionTarget.DIRECT),
                    SubscriptionRule("AND,((RULE-SET,voice),(NETWORK,udp))", listOf("no-resolve"), SubscriptionTarget.BLOCK)
                ),
                providers = listOf(provider),
                skipped = emptyList()
            )
        )
        val engine = engine(
            listOf(vless), "p1", listOf("org.dpi"), listOf("org.vpn"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.VPN))
        ).copy(subscriptionRules = subscription)
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject

        assertEquals(
            listOf(
                "IN-NAME,probe-in,p1",
                "DOMAIN-SUFFIX,example.org,p1",
                "DOMAIN-SUFFIX,openai.com,p1",
                "RULE-SET,sub7-voice,DIRECT",
                "AND,((RULE-SET,sub7-voice),(NETWORK,udp)),REJECT,no-resolve",
                "PROCESS-NAME,org.dpi,byedpi",
                "PROCESS-NAME,org.vpn,p1",
                "MATCH,REJECT"
            ),
            config.rules()
        )
        assertEquals(listOf("sub7-voice"), config.getValue("rule-providers").jsonObject.keys.toList())
        assertEquals(provider.definition, config.getValue("rule-providers").jsonObject.getValue("sub7-voice"))
    }

    @Test
    fun managedSubscriptionListsAreFileProvidersAndTheRestPassesThrough() {
        val url = "https://example.org/voice.mrs"
        val managed = SubscriptionProvider(
            "voice",
            JsonObject(mapOf("type" to JsonPrimitive("http"), "format" to JsonPrimitive("mrs"), "behavior" to JsonPrimitive("domain"), "url" to JsonPrimitive(url)))
        )
        val classical = SubscriptionProvider(
            "extra",
            JsonObject(mapOf("type" to JsonPrimitive("http"), "format" to JsonPrimitive("yaml"), "behavior" to JsonPrimitive("classical"), "url" to JsonPrimitive("https://example.org/extra.yaml")))
        )
        val subscription = AppliedSubscriptionRules(
            7,
            SubscriptionRules(
                rules = listOf(
                    SubscriptionRule("RULE-SET,voice", emptyList(), SubscriptionTarget.DIRECT),
                    SubscriptionRule("RULE-SET,extra", emptyList(), SubscriptionTarget.DIRECT)
                ),
                providers = listOf(managed, classical),
                skipped = emptyList()
            )
        )
        val engine = engine(listOf(vless), "p1", emptyList(), listOf("org.vpn")).copy(subscriptionRules = subscription)
        val providers = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android()))
            .jsonObject.getValue("rule-providers").jsonObject

        val file = providers.getValue("sub7-voice").jsonObject
        assertEquals("file", file.getValue("type").jsonPrimitive.content)
        assertEquals("domain", file.getValue("behavior").jsonPrimitive.content)
        assertEquals("mrs", file.getValue("format").jsonPrimitive.content)
        assertEquals(RuleLists.path(url, RuleSetKind.DOMAIN), file.getValue("path").jsonPrimitive.content)
        assertEquals(classical.definition, providers.getValue("sub7-extra"))
    }

    @Test
    fun withoutSubscriptionRulesNothingIsAddedAndThereAreNoProviders() {
        val config = build(platform = MihomoPlatform.Android(), vpn = listOf("org.vpn"))

        assertFalse(config.containsKey("rule-providers"))
        assertEquals(4, config.rules().size)
    }

    @Test
    fun aWholeGroupIsTheMainExitAsALoadBalancerThatKeepsASiteOnOneServer() {
        val second = vless.copy(tag = "p2")
        val engine = engine(
            listOf(vless, second), "g9", emptyList(), listOf("org.vpn"),
            rules = listOf(RoutingRule(RuleMatch.DOMAIN_SUFFIX, "example.org", RuleTarget.VPN))
        ).copy(balancer = EngineBalancer("g9", listOf("p1", "p2")))
        val config = Json.parseToJsonElement(MihomoConfigBuilder.build(engine, PROBE, MihomoPlatform.Android())).jsonObject

        val group = config.getValue("proxy-groups").jsonArray.single().jsonObject
        assertEquals("g9", group.getValue("name").jsonPrimitive.content)
        assertEquals("load-balance", group.getValue("type").jsonPrimitive.content)
        assertEquals("consistent-hashing", group.getValue("strategy").jsonPrimitive.content)
        assertEquals(listOf("p1", "p2"), group.getValue("proxies").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("IN-NAME,probe-in,g9", "DOMAIN-SUFFIX,example.org,g9", "PROCESS-NAME,org.vpn,g9", "MATCH,REJECT"),
            config.rules()
        )
        assertTrue(config.getValue("dns").jsonObject.getValue("nameserver").jsonArray.single().jsonPrimitive.content.endsWith("#g9"))
    }

    @Test
    fun aSingleServerHasNoProxyGroups() {
        assertFalse(build(vpn = listOf("org.vpn")).containsKey("proxy-groups"))
    }

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
