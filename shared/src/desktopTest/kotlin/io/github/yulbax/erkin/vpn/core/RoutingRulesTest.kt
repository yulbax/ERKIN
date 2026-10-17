package io.github.yulbax.erkin.vpn.core

import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingRulesTest {
    @Test
    fun domainZonesAreNormalizedToTheFormTheCoreMatches() {
        assertEquals("ru", RuleMatch.DOMAIN_SUFFIX.normalize(" .RU "))
        assertEquals("xn--p1ai", RuleMatch.DOMAIN_SUFFIX.normalize("рф"))
        assertEquals("youtube.com", RuleMatch.DOMAIN_SUFFIX.normalize("*.youtube.com"))
        assertEquals("www.youtube.com", RuleMatch.DOMAIN.normalize("https://www.youtube.com/watch?v=1"))
        assertEquals("рф", RuleMatch.DOMAIN_SUFFIX.display("xn--p1ai"))
    }

    @Test
    fun valuesThatWouldBreakTheConfigAreRejected() {
        assertNull(RuleMatch.DOMAIN_SUFFIX.normalize(""))
        assertNull(RuleMatch.DOMAIN_SUFFIX.normalize("a,b"))
        assertNull(RuleMatch.DOMAIN.normalize("two words"))
        assertNull(RuleMatch.DOMAIN_KEYWORD.normalize("go,ogle"))
        assertNull(RuleMatch.IP_CIDR.normalize("example.com"))
        assertNull(RuleMatch.IP_CIDR.normalize("300.1.1.1"))
        assertNull(RuleMatch.IP_CIDR.normalize("10.0.0.0/33"))
    }

    @Test
    fun addressesGetAPrefix() {
        assertEquals("1.1.1.1/32", RuleMatch.IP_CIDR.normalize("1.1.1.1"))
        assertEquals("10.0.0.0/8", RuleMatch.IP_CIDR.normalize("10.0.0.0/8"))
        assertEquals("2001:db8:0:0:0:0:0:0/32", RuleMatch.IP_CIDR.normalize("2001:DB8::/32"))
        assertEquals("google", RuleMatch.DOMAIN_KEYWORD.normalize("Google"))
    }

    @Test
    fun countriesAreIpSetsNamedByIsoCode() {
        val russia = RuleLists.country("RU")
        assertEquals(RuleSetKind.IPCIDR, russia.kind)
        assertEquals("RU", RuleLists.countryOf(russia.url))
        assertNull(RuleLists.countryOf(RuleLists.COUNTRY_URL_PREFIX + "xx" + RuleLists.COUNTRY_URL_SUFFIX))
        assertNull(RuleLists.countryOf("https://example.com/ru.mrs"))
    }

    @Test
    fun legacyCountryAndListRulesBecomeRuleSets() {
        val country = RoutingRuleEntity(type = "country", value = "RU", target = "DIRECT").upgraded()
        val list = RoutingRuleEntity(type = "list", value = "https://example.com/ads.txt", target = "BLOCK").upgraded()

        assertEquals(RoutingRule(RuleMatch.RULE_SET, RuleLists.country("RU").url, RuleTarget.DIRECT), country.toRule())
        assertEquals(RoutingRule(RuleMatch.RULE_SET, "https://example.com/ads.txt", RuleTarget.BLOCK), list.toRule())
    }
}
