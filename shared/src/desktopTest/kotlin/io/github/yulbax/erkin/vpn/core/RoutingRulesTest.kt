package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingRulesTest {
    @Test
    fun domainZonesAreNormalizedToTheFormTheCoreMatches() {
        assertEquals("ru", RoutingRules.normalize(RuleMatch.DOMAIN_SUFFIX, " .RU "))
        assertEquals("xn--p1ai", RoutingRules.normalize(RuleMatch.DOMAIN_SUFFIX, "рф"))
        assertEquals("youtube.com", RoutingRules.normalize(RuleMatch.DOMAIN_SUFFIX, "*.youtube.com"))
        assertEquals("www.youtube.com", RoutingRules.normalize(RuleMatch.DOMAIN, "https://www.youtube.com/watch?v=1"))
        assertEquals("рф", RoutingRules.display(RuleMatch.DOMAIN_SUFFIX, "xn--p1ai"))
    }

    @Test
    fun valuesThatWouldBreakTheConfigAreRejected() {
        assertNull(RoutingRules.normalize(RuleMatch.DOMAIN_SUFFIX, ""))
        assertNull(RoutingRules.normalize(RuleMatch.DOMAIN_SUFFIX, "a,b"))
        assertNull(RoutingRules.normalize(RuleMatch.DOMAIN, "two words"))
        assertNull(RoutingRules.normalize(RuleMatch.DOMAIN_KEYWORD, "go,ogle"))
        assertNull(RoutingRules.normalize(RuleMatch.IP_CIDR, "example.com"))
        assertNull(RoutingRules.normalize(RuleMatch.IP_CIDR, "300.1.1.1"))
        assertNull(RoutingRules.normalize(RuleMatch.IP_CIDR, "10.0.0.0/33"))
    }

    @Test
    fun addressesGetAPrefix() {
        assertEquals("1.1.1.1/32", RoutingRules.normalize(RuleMatch.IP_CIDR, "1.1.1.1"))
        assertEquals("10.0.0.0/8", RoutingRules.normalize(RuleMatch.IP_CIDR, "10.0.0.0/8"))
        assertEquals("2001:db8:0:0:0:0:0:0/32", RoutingRules.normalize(RuleMatch.IP_CIDR, "2001:DB8::/32"))
        assertEquals("google", RoutingRules.normalize(RuleMatch.DOMAIN_KEYWORD, "Google"))
    }
}
