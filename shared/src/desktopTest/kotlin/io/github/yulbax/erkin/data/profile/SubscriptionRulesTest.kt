package io.github.yulbax.erkin.data.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRulesTest {

    @Test
    fun rulesProvidersAndSkippedSurviveAStorageRoundTrip() {
        val rules = SubscriptionRules(
            rules = listOf(
                SubscriptionRule("DOMAIN-SUFFIX,openai.com", emptyList(), SubscriptionTarget.VPN),
                SubscriptionRule("IP-CIDR,1.2.3.0/24", listOf("no-resolve"), SubscriptionTarget.BLOCK)
            ),
            providers = listOf(
                SubscriptionProvider(
                    "ads",
                    JsonObject(mapOf("type" to JsonPrimitive("inline"), "payload" to JsonArray(listOf(JsonPrimitive("DOMAIN,a.example")))))
                )
            ),
            skipped = listOf(SkippedRule("GEOIP,CN,DIRECT", SkipReason.GEODATA))
        )

        assertEquals(rules, SubscriptionRules.decode(rules.encode()))
    }

    @Test
    fun garbageOrMissingStorageDecodesToNothing() {
        assertNull(SubscriptionRules.decode(null))
        assertNull(SubscriptionRules.decode("not json"))
        assertNull(SubscriptionRules.decode("{}"))
    }

    @Test
    fun providerReferencesAreRenamedInsideNestedConditions() {
        val rule = SubscriptionRule("AND,((RULE-SET,voice),(NETWORK,udp),(RULE-SET,ads))", emptyList(), SubscriptionTarget.DIRECT)

        assertEquals(listOf("voice", "ads"), rule.providers)
        assertEquals("AND,((RULE-SET,x-voice),(NETWORK,udp),(RULE-SET,x-ads))", rule.conditionWith { "x-$it" })
    }

    @Test
    fun onlyHttpMrsAndTextListsWithAKnownBehaviorAreManaged() {
        fun provider(vararg pairs: Pair<String, String>) =
            SubscriptionProvider("p", JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) }))
        val url = "url" to "https://example.org/list"

        assertEquals(
            ManagedList("https://example.org/list", RuleSetKind.DOMAIN),
            provider("type" to "http", "format" to "mrs", "behavior" to "domain", url).managed
        )
        assertEquals(
            ManagedList("https://example.org/list", RuleSetKind.IPCIDR),
            provider("type" to "HTTP", "format" to "text", "behavior" to "ipcidr", url).managed
        )
        assertNull(provider("type" to "http", "behavior" to "domain", url).managed)
        assertNull(provider("type" to "http", "format" to "yaml", "behavior" to "domain", url).managed)
        assertNull(provider("type" to "http", "format" to "mrs", "behavior" to "classical", url).managed)
        assertNull(provider("type" to "inline", "format" to "mrs", "behavior" to "domain", url).managed)
    }

    @Test
    fun rulesOfMissingManagedListsAreLeftOutWithTheirProviders() {
        val ready = managedProvider("ready", "https://example.org/ready.mrs")
        val missing = managedProvider("missing", "https://example.org/missing.mrs")
        val rules = SubscriptionRules(
            rules = listOf(
                SubscriptionRule("RULE-SET,ready", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("RULE-SET,missing", emptyList(), SubscriptionTarget.DIRECT),
                SubscriptionRule("AND,((RULE-SET,ready),(NOT,((RULE-SET,missing)))),DIRECT", emptyList(), SubscriptionTarget.BLOCK),
                SubscriptionRule("DOMAIN,a.example", emptyList(), SubscriptionTarget.VPN)
            ),
            providers = listOf(ready, missing),
            skipped = emptyList()
        )

        val available = rules.available { it.url == "https://example.org/ready.mrs" }

        assertEquals(listOf("RULE-SET,ready", "DOMAIN,a.example"), available.rules.map { it.condition })
        assertEquals(listOf(ready), available.providers)
        assertEquals(rules, rules.available { true })
        assertTrue(rules.isWaiting(rules.rules[1], setOf("https://example.org/missing.mrs")))
        assertFalse(rules.isWaiting(rules.rules[0], setOf("https://example.org/missing.mrs")))
    }

    @Test
    fun convertedGeoRulesDisplayAsTheirOriginalType() {
        assertEquals("GEOIP" to "RU", SubscriptionRule("RULE-SET,geoip-ru", emptyList(), SubscriptionTarget.DIRECT).display)
        assertEquals("GEOSITE" to "YOUTUBE", SubscriptionRule("RULE-SET,geosite-youtube", emptyList(), SubscriptionTarget.DIRECT).display)
        assertEquals("RULE-SET" to "voice", SubscriptionRule("RULE-SET,voice", emptyList(), SubscriptionTarget.DIRECT).display)
    }

    @Test
    fun aBlobStoredBeforeGeoRulesWereConvertedStillDecodes() {
        val blob = """{"rules":[{"condition":"DOMAIN,a.example","options":[],"target":"VPN"}],"providers":{},"skipped":[{"text":"GEOIP,CN,DIRECT","reason":"GEODATA"}]}"""

        assertEquals(listOf(SkippedRule("GEOIP,CN,DIRECT", SkipReason.GEODATA)), SubscriptionRules.decode(blob)?.skipped)
    }

    @Test
    fun theStoredFormatStaysTheSame() {
        val rules = SubscriptionRules(
            rules = listOf(SubscriptionRule("RULE-SET,ads", listOf("no-resolve"), SubscriptionTarget.BLOCK)),
            providers = listOf(managedProvider("ads", "https://example.org/ads.mrs")),
            skipped = listOf(SkippedRule("IP-ASN,13335,DIRECT", SkipReason.GEODATA))
        )

        assertEquals(
            """{"rules":[{"condition":"RULE-SET,ads","options":["no-resolve"],"target":"BLOCK"}],""" +
                """"providers":{"ads":{"type":"http","format":"mrs","behavior":"domain","url":"https://example.org/ads.mrs"}},""" +
                """"skipped":[{"text":"IP-ASN,13335,DIRECT","reason":"GEODATA"}]}""",
            rules.encode()
        )
    }

    private fun managedProvider(name: String, url: String) = SubscriptionProvider(
        name,
        JsonObject(mapOf("type" to JsonPrimitive("http"), "format" to JsonPrimitive("mrs"), "behavior" to JsonPrimitive("domain"), "url" to JsonPrimitive(url)))
    )

    @Test
    fun anEmptyResultHasNeitherRulesNorSkipped() {
        assertTrue(SubscriptionRules(emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
