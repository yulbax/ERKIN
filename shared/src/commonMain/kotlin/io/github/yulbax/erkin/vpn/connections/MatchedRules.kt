package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.data.profile.AppliedSubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleValues

data class CoreRule(val type: String, val payload: String)

sealed interface MatchedRule {
    data class Own(val rule: RoutingRule) : MatchedRule

    data class Subscription(val rule: SubscriptionRule) : MatchedRule
}

class RuleIndex(own: List<RoutingRule>, subscriptions: List<AppliedSubscriptionRules>) {
    private val ownConditions = own.map { it to conditionsOf(it) }
    private val subscriptionConditions = subscriptions.flatMap { applied ->
        applied.rules.rules.map { rule ->
            rule to RuleCondition.parse(rule.conditionWith { SubscriptionRules.providerName(applied.groupId, it) })
        }
    }

    fun match(rule: CoreRule, app: String): MatchedRule? {
        val conditions = RuleCondition.parse(rule.type, rule.payload)
        val (processes, rest) = conditions.partition { it.isProcess }
        val forApp = processes.isNotEmpty()
        rest.singleOrNull()?.let { condition ->
            ownConditions.firstOrNull { (own, matches) -> own.app == app.takeIf { forApp } && condition in matches }
                ?.let { return MatchedRule.Own(it.first) }
        }
        return subscriptionConditions.firstOrNull { it.second == conditions }?.let { MatchedRule.Subscription(it.first) }
    }

    companion object {
        val EMPTY = RuleIndex(emptyList(), emptyList())

        private fun conditionsOf(rule: RoutingRule): Set<RuleCondition> = when (val url = RuleLists.url(rule)) {
            null -> setOf(RuleCondition.of(rule.match.mihomo, rule.value))
            else -> RuleSetKind.entries.map { RuleCondition.of(rule.match.mihomo, RuleLists.providerName(url, it)) }.toSet()
        }
    }
}

private data class RuleCondition(val type: String, val value: String) {
    val isProcess: Boolean get() = type in PROCESS_TYPES

    companion object {
        private val PROCESS_TYPES = setOf("PROCESSNAME", "PROCESSPATH")
        private const val IP_CIDR = "IPCIDR"
        private val LOGIC_TYPES = setOf("AND", "OR", "NOT")
        private val NESTED = Regex("""\(([A-Za-z-]+),([^()]*)\)""")

        fun of(type: String, value: String): RuleCondition {
            val key = type.replace("-", "").uppercase()
            val text = value.trim().lowercase()
            return RuleCondition(key, if (key == IP_CIDR) RuleValues.Cidr.normalize(text) ?: text else text)
        }

        fun parse(type: String, payload: String): List<RuleCondition> =
            if (of(type, "").type in LOGIC_TYPES) NESTED.findAll(payload).map { of(it.groupValues[1], it.groupValues[2]) }.toList()
            else listOf(of(type, payload))

        fun parse(condition: String): List<RuleCondition> =
            parse(condition.substringBefore(SubscriptionRule.TOKEN_SEPARATOR), condition.substringAfter(SubscriptionRule.TOKEN_SEPARATOR, ""))
    }
}
