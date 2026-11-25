package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.data.profile.ManagedList
import io.github.yulbax.erkin.data.profile.SkipReason
import io.github.yulbax.erkin.data.profile.SkippedRule
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashRulesReaderTest {

    @Test
    fun targetsFollowSelectAndUrlTestGroupsToTheirChannel() {
        val rules = requireNotNull(ClashRulesReader.read(GROUPED))

        assertEquals(
            listOf(
                SubscriptionRule("DOMAIN-SUFFIX,openai.com", emptyList(), SubscriptionTarget.VPN),
                SubscriptionRule("DOMAIN-SUFFIX,yandex.ru", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("DOMAIN-SUFFIX,games.example", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("DOMAIN,direct.example", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("DOMAIN,ads.example", emptyList(), SubscriptionTarget.BLOCK),
                SubscriptionRule("DOMAIN,dropped.example", emptyList(), SubscriptionTarget.BLOCK),
                SubscriptionRule("DOMAIN,single.example", emptyList(), SubscriptionTarget.VPN)
            ),
            rules.rules
        )
    }

    @Test
    fun matchIsDroppedSilently() {
        val rules = requireNotNull(ClashRulesReader.read(GROUPED))

        assertFalse(rules.rules.any { it.type == "MATCH" })
        assertFalse(rules.skipped.any { it.text.startsWith("MATCH") })
    }

    @Test
    fun passAndUnknownGroupsHaveNoTarget() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                proxies: [{name: s1, type: ss, server: a, port: 1}]
                rules:
                  - DOMAIN,a.example,PASS
                  - DOMAIN,b.example,Nowhere
                  - DOMAIN,c.example,s1
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(SkippedRule("DOMAIN,a.example,PASS", SkipReason.NO_TARGET), SkippedRule("DOMAIN,b.example,Nowhere", SkipReason.NO_TARGET)),
            rules.skipped
        )
        assertEquals(listOf(SubscriptionTarget.VPN), rules.rules.map { it.target })
    }

    @Test
    fun aCycleOfSelectGroupsHasNoTarget() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                proxy-groups:
                  - {name: A, type: select, proxies: [B]}
                  - {name: B, type: select, proxies: [A]}
                rules:
                  - DOMAIN,a.example,A
                  - DOMAIN,b.example,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(listOf(SkippedRule("DOMAIN,a.example,A", SkipReason.NO_TARGET)), rules.skipped)
    }

    @Test
    fun aSelectGroupWithOnlyProviderServersGoesThroughTheVpn() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                proxy-groups:
                  - {name: All, type: select, include-all: true}
                rules:
                  - DOMAIN,a.example,All
                """.trimIndent()
            )
        )

        assertEquals(listOf(SubscriptionTarget.VPN), rules.rules.map { it.target })
    }

    @Test
    fun geoRulesBecomeMetaCubeXListsWithTheirOptionsKept() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rules:
                  - GEOIP,CN,DIRECT,no-resolve
                  - GEOSITE,Category-Ads,REJECT
                  - GEOIP,LAN,DIRECT
                  - DOMAIN,a.example,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                SubscriptionRule("RULE-SET,geoip-cn", listOf("no-resolve"), SubscriptionTarget.DIRECT),
                SubscriptionRule("RULE-SET,geosite-category-ads", emptyList(), SubscriptionTarget.BLOCK),
                SubscriptionRule("RULE-SET,geoip-private", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("DOMAIN,a.example", emptyList(), SubscriptionTarget.DIRECT)
            ),
            rules.rules
        )
        assertTrue(rules.skipped.isEmpty())
        assertEquals(
            listOf(
                ManagedList(RuleLists.country("cn").url, RuleSetKind.IPCIDR),
                ManagedList(RuleLists.geoSite("category-ads").url, RuleSetKind.DOMAIN),
                ManagedList(RuleLists.country("private").url, RuleSetKind.IPCIDR)
            ),
            rules.managedLists
        )
        assertTrue(RuleLists.country("cn").url.endsWith("/geo/geoip/cn.mrs"))
        assertTrue(RuleLists.geoSite("category-ads").url.endsWith("/geo/geosite/category-ads.mrs"))
    }

    @Test
    fun geoRulesInsideCompositesAreRewritten() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rules:
                  - AND,((GEOIP,RU,no-resolve),(NETWORK,udp)),DIRECT
                  - OR,((GEOSITE,youtube),(NOT,((GEOIP,RU)))),REJECT
                  - GEOIP,RU,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                "AND,((RULE-SET,geoip-ru,no-resolve),(NETWORK,udp))",
                "OR,((RULE-SET,geosite-youtube),(NOT,((RULE-SET,geoip-ru))))",
                "RULE-SET,geoip-ru"
            ),
            rules.rules.map { it.condition }
        )
        assertEquals(listOf("geoip-ru", "geosite-youtube"), rules.providers.map { it.name })
    }

    @Test
    fun synthesizedProviderNamesDoNotClashWithTheSubscriptionsOwn() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rule-providers:
                  geoip-cn: {type: http, behavior: classical, url: "https://example.org/cn.yaml"}
                rules:
                  - RULE-SET,geoip-cn,DIRECT
                  - GEOIP,CN,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(listOf("RULE-SET,geoip-cn", "RULE-SET,geoip-cn-1"), rules.rules.map { it.condition })
        assertEquals(listOf("geoip-cn", "geoip-cn-1"), rules.providers.map { it.name })
        assertEquals(listOf(ManagedList(RuleLists.country("cn").url, RuleSetKind.IPCIDR)), rules.managedLists)
    }

    @Test
    fun asnAndSourceGeoRulesAreStillSkipped() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rules:
                  - IP-ASN,13335,DIRECT
                  - SRC-GEOIP,CN,DIRECT
                  - AND,((SRC-IP-ASN,1),(NETWORK,udp)),DIRECT
                  - GEOIP,bad/code,DIRECT
                  - DOMAIN,a.example,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                SkippedRule("IP-ASN,13335,DIRECT", SkipReason.GEODATA),
                SkippedRule("SRC-GEOIP,CN,DIRECT", SkipReason.GEODATA),
                SkippedRule("AND,((SRC-IP-ASN,1),(NETWORK,udp)),DIRECT", SkipReason.GEODATA),
                SkippedRule("GEOIP,bad/code,DIRECT", SkipReason.UNSUPPORTED)
            ),
            rules.skipped
        )
        assertEquals(1, rules.rules.size)
    }

    @Test
    fun unknownAndSubRuleTypesAreSkippedAsUnsupported() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rules:
                  - SUB-RULE,(NETWORK,tcp),inner
                  - SCRIPT,shortcut,DIRECT
                  - OR,((DOMAIN,a.example),(SCRIPT,x)),DIRECT
                  - DOMAIN,a.example,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(listOf(SkipReason.UNSUPPORTED, SkipReason.UNSUPPORTED, SkipReason.UNSUPPORTED), rules.skipped.map { it.reason })
        assertEquals(1, rules.rules.size)
    }

    @Test
    fun ruleSetsPointingAtAMissingOrFileProviderAreSkipped() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rule-providers:
                  local: {type: file, behavior: domain, path: ./local.yaml}
                  remote: {type: http, behavior: domain, format: mrs, url: 'https://example.org/remote.mrs'}
                rules:
                  - RULE-SET,missing,DIRECT
                  - RULE-SET,local,DIRECT
                  - AND,((RULE-SET,missing),(NETWORK,udp)),DIRECT
                  - RULE-SET,remote,DIRECT
                """.trimIndent()
            )
        )

        assertEquals(List(3) { SkipReason.NO_PROVIDER }, rules.skipped.map { it.reason })
        assertEquals(listOf("RULE-SET,remote"), rules.rules.map { it.condition })
    }

    @Test
    fun compositeRulesKeepTheirNestedConditionAndTrailingOptions() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rule-providers:
                  voice: {type: inline, behavior: classical, payload: ['DST-PORT,50000-50100']}
                rules:
                  - AND,((RULE-SET,voice),(NETWORK,udp),(DST-PORT,50000-50100)),DIRECT,no-resolve
                  - OR,((DST-PORT,5223),(DOMAIN-SUFFIX,push.apple.com)),DIRECT
                  - IP-CIDR,1.2.3.0/24,DIRECT,no-resolve
                  - PROCESS-NAME-REGEX,(?i).*anydesk.*,DIRECT
                """.trimIndent()
            )
        )

        val first = rules.rules[0]
        assertEquals("AND,((RULE-SET,voice),(NETWORK,udp),(DST-PORT,50000-50100))", first.condition)
        assertEquals(listOf("no-resolve"), first.options)
        assertEquals("AND", first.type)
        assertEquals("((RULE-SET,voice),(NETWORK,udp),(DST-PORT,50000-50100))", first.value)
        assertEquals(listOf("voice"), first.providers)
        assertEquals("OR,((DST-PORT,5223),(DOMAIN-SUFFIX,push.apple.com))", rules.rules[1].condition)
        assertEquals("IP-CIDR,1.2.3.0/24", rules.rules[2].condition)
        assertEquals(listOf("no-resolve"), rules.rules[2].options)
        assertEquals("PROCESS-NAME-REGEX,(?i).*anydesk.*", rules.rules[3].condition)
    }

    @Test
    fun keptProvidersLoseTheirPathAndOnlyUsedOnesAreStored() {
        val rules = requireNotNull(
            ClashRulesReader.read(
                """
                rule-providers:
                  ads: {type: http, behavior: domain, format: mrs, url: 'https://example.org/ads.mrs', path: ./ads.mrs, interval: 86400}
                  quic: {type: inline, behavior: classical, payload: ['AND,((NETWORK,udp),(DST-PORT,443))']}
                  unused: {type: http, behavior: domain, url: 'https://example.org/unused.mrs'}
                  broken: {type: http, behavior: domain}
                rules:
                  - RULE-SET,ads,REJECT
                  - RULE-SET,quic,REJECT
                  - RULE-SET,broken,REJECT
                """.trimIndent()
            )
        )

        assertEquals(listOf("ads", "quic"), rules.providers.map { it.name })
        assertFalse("path" in rules.providers[0].definition)
        assertEquals("https://example.org/ads.mrs", rules.providers[0].definition.getValue("url").jsonPrimitive.content)
        assertNotNull(rules.providers[1].definition["payload"])
        assertEquals(listOf(SkipReason.NO_PROVIDER), rules.skipped.map { it.reason })
    }

    @Test
    fun aBodyWithoutClashRulesGivesNothing() {
        assertNull(ClashRulesReader.read("vless://id@host:443#name"))
        assertNull(ClashRulesReader.read("proxies:\n  - {name: a, type: ss, server: a, port: 1}\n"))
        assertNull(ClashRulesReader.read("- just\n- a list\n"))
        assertNull(ClashRulesReader.read("rules:\n  - MATCH,DIRECT\n"))
    }

    private companion object {
        val GROUPED = """
            proxies:
              - {name: 'Single', type: ss, server: a, port: 1, cipher: aes-128-gcm, password: p}
            proxy-groups:
              - {name: 'Foreign', type: select, proxies: ['Fastest', DIRECT]}
              - {name: 'Fastest', type: url-test, proxies: ['NL'], url: 'https://example.org', interval: 300}
              - {name: 'NL', type: url-test, proxies: ['Single']}
              - {name: 'Russian', type: select, proxies: [DIRECT, 'Foreign']}
              - {name: 'Games', type: select, proxies: [DIRECT]}
            rules:
              - DOMAIN-SUFFIX,openai.com,Foreign
              - DOMAIN-SUFFIX,yandex.ru,Russian
              - DOMAIN-SUFFIX,games.example,Games
              - DOMAIN,direct.example,DIRECT
              - DOMAIN,ads.example,REJECT
              - DOMAIN,dropped.example,REJECT-DROP
              - DOMAIN,single.example,Single
              - MATCH,Foreign
        """.trimIndent()
    }
}
