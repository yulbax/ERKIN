package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.data.profile.AppliedSubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchedRulesTest {
    private val filter = RoutingRule(RuleMatch.RULE_SET, FILTER_URL, RuleTarget.BLOCK)
    private val suffix = RoutingRule(RuleMatch.DOMAIN_SUFFIX, "opendns.com", RuleTarget.DIRECT)
    private val appRule = RoutingRule(RuleMatch.DOMAIN_SUFFIX, "opendns.com", RuleTarget.VPN, app = BROWSER)
    private val voice = SubscriptionRule("AND,((RULE-SET,voice),(NETWORK,udp))", emptyList(), SubscriptionTarget.DIRECT)
    private val openai = SubscriptionRule("DOMAIN-SUFFIX,openai.com", emptyList(), SubscriptionTarget.VPN)
    private val index = RuleIndex(
        listOf(filter, suffix, appRule),
        listOf(AppliedSubscriptionRules(3, SubscriptionRules(listOf(voice, openai), emptyList(), emptyList())))
    )

    @Test
    fun aListRuleIsFoundByEitherOfItsProviders() {
        RuleSetKind.entries.forEach { kind ->
            assertEquals(MatchedRule.Own(filter), index.match(CoreRule("RuleSet", RuleLists.providerName(FILTER_URL, kind)), BROWSER))
        }
    }

    @Test
    fun aValueRuleIsFoundByItsTypeAndValue() {
        assertEquals(MatchedRule.Own(suffix), index.match(CoreRule("DomainSuffix", "opendns.com"), BROWSER))
        assertNull(index.match(CoreRule("Domain", "opendns.com"), BROWSER))
    }

    @Test
    fun anIpv6RangeIsFoundInTheCoresShortForm() {
        val range = RoutingRule(RuleMatch.IP_CIDR, RuleMatch.IP_CIDR.normalize("2001:db8::/32")!!, RuleTarget.BLOCK)

        assertEquals(MatchedRule.Own(range), RuleIndex(listOf(range), emptyList()).match(CoreRule("IPCIDR", "2001:db8::/32"), BROWSER))
    }

    @Test
    fun anAppRuleIsFoundOnlyForItsApp() {
        val android = CoreRule("AND", "((ProcessName,$BROWSER) && (DomainSuffix,opendns.com))")
        val desktop = CoreRule("AND", "((OR,((ProcessName,$BROWSER) || (ProcessName,$BROWSER.exe))) && (DomainSuffix,opendns.com))")

        assertEquals(MatchedRule.Own(appRule), index.match(android, BROWSER))
        assertEquals(MatchedRule.Own(appRule), index.match(desktop, BROWSER))
        assertNull(index.match(android, "org.other"))
    }

    @Test
    fun subscriptionRulesAreFoundWithTheirRenamedLists() {
        assertEquals(MatchedRule.Subscription(voice), index.match(CoreRule("AND", "((RuleSet,sub3-voice) && (Network,udp))"), BROWSER))
        assertEquals(MatchedRule.Subscription(openai), index.match(CoreRule("DomainSuffix", "openai.com"), BROWSER))
        assertNull(index.match(CoreRule("RuleSet", "sub4-voice"), BROWSER))
    }

    @Test
    fun theUsersRuleWinsOverTheSameSubscriptionRule() {
        val both = RuleIndex(listOf(suffix), listOf(AppliedSubscriptionRules(3, SubscriptionRules(listOf(SubscriptionRule("DOMAIN-SUFFIX,opendns.com", emptyList(), SubscriptionTarget.VPN)), emptyList(), emptyList()))))

        assertEquals(MatchedRule.Own(suffix), both.match(CoreRule("DomainSuffix", "opendns.com"), BROWSER))
    }

    @Test
    fun eachAddressKeepsTheRuleThatRoutedItAndAppChannelsHaveNone() {
        val table = ConnectionTable.parse(
            """{"rows":[
                {"process":"$BROWSER","host":"doh.opendns.com","route":"REJECT","rule":"RuleSet","payload":"list-a-domain","connections":2,"lastSeen":5},
                {"process":"$BROWSER","host":"api.opendns.com","route":"REJECT","rule":"DomainSuffix","payload":"opendns.com","connections":1,"lastSeen":6},
                {"process":"$BROWSER","host":"example.org","route":"DIRECT","rule":"ProcessName","payload":"$BROWSER","connections":1,"lastSeen":7}
            ]}"""
        )
        val hosts = ConnectionLedger.activity(table).single().hosts

        val blocked = hosts.first { it.host == "opendns.com" }.addresses.associate { it.name to it.rule }
        assertEquals(CoreRule("RuleSet", "list-a-domain"), blocked["doh.opendns.com"])
        assertEquals(CoreRule("DomainSuffix", "opendns.com"), blocked["api.opendns.com"])
        assertNull(hosts.first { it.host == "example.org" }.addresses.single().rule)
    }

    private companion object {
        const val BROWSER = "org.mozilla.firefox"
        const val FILTER_URL = "https://filters.adtidy.org/extension/ublock/filters/1.txt"
    }
}
