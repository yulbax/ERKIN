package io.github.yulbax.erkin.rulelist

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListParserTest {

    @Test
    fun plainDomainListsMatchSubdomainsAndKeepTheTitle() {
        val parsed = parse(
            "# Title: HaGeZi's Multi NORMAL",
            "# Syntax: Domains",
            "ads.example.com",
            "Tracker.Example.org",
            "ads.example.com",
            "localhost",
            "com"
        )
        assertEquals("HaGeZi's Multi NORMAL", parsed.title)
        assertEquals(listOf("+.ads.example.com", "+.tracker.example.org"), parsed.domains)
    }

    @Test
    fun hostsFilesKeepOnlyTheBlockedNames() {
        val parsed = parse(
            "127.0.0.1 localhost",
            "::1 localhost",
            "0.0.0.0 0.0.0.0",
            "0.0.0.0 ads.example.com # banner",
            "127.0.0.1\tmetrics.example.net",
            "192.168.1.1 router.lan"
        )
        assertEquals(listOf("+.ads.example.com", "+.metrics.example.net"), parsed.domains)
        assertEquals(emptyList<String>(), parsed.cidrs)
    }

    @Test
    fun adblockListsKeepOnlyDomainBlocks() {
        val parsed = parse(
            "[Adblock Plus 2.0]",
            "! Title: AdGuard DNS filter",
            "||ads.example.com^",
            "||pixel.example.com^\$important",
            "@@||allowed.example.com^",
            "||cdn.example.com^\$third-party",
            "example.com##.banner",
            "/banner/*/img^",
            "||example.com/path^"
        )
        assertEquals("AdGuard DNS filter", parsed.title)
        assertEquals(listOf("+.ads.example.com", "+.pixel.example.com"), parsed.domains)
    }

    @Test
    fun mihomoListsKeepExactDomainsAndCidrs() {
        val parsed = parse(
            "payload:",
            "  - '+.wild.example.com'",
            "  - 'exact.example.com'",
            ".dot.example.com",
            "DOMAIN,classic.example.com",
            "DOMAIN-SUFFIX,suffix.example.com",
            "DOMAIN-KEYWORD,ads",
            "IP-CIDR,10.0.0.0/8,no-resolve",
            "IP-CIDR6,2001:db8::/32",
            "1.2.3.4",
            "1password.com"
        )
        assertEquals(
            listOf("+.wild.example.com", "+.exact.example.com", "+.dot.example.com", "classic.example.com", "+.suffix.example.com", "+.1password.com"),
            parsed.domains
        )
        assertEquals(listOf("10.0.0.0/8", "2001:db8:0:0:0:0:0:0/32", "1.2.3.4/32"), parsed.cidrs)
    }

    private fun parse(vararg lines: String) = RuleListParser.parse(lines.asSequence())
}
