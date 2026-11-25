package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SubscriptionTarget(val wire: String) {
    VPN("VPN"),
    DIRECT("DIRECT"),
    BLOCK("BLOCK");

    companion object {
        fun fromWire(wire: String?): SubscriptionTarget? = entries.firstOrNull { it.wire == wire }
    }
}

enum class SkipReason(val wire: String) {
    GEODATA("GEODATA"),
    UNSUPPORTED("UNSUPPORTED"),
    NO_TARGET("NO_TARGET"),
    NO_PROVIDER("NO_PROVIDER");

    companion object {
        fun fromWire(wire: String?): SkipReason? = entries.firstOrNull { it.wire == wire }
    }
}

data class SubscriptionRule(val condition: String, val options: List<String>, val target: SubscriptionTarget) {
    val type: String get() = condition.substringBefore(TOKEN_SEPARATOR)

    val value: String get() = condition.substringAfter(TOKEN_SEPARATOR, "")

    val display: Pair<String, String>
        get() = (if (type == RULE_SET) GeoList.of(value) else null)?.let { (list, code) -> list.name to code.uppercase() } ?: (type to value)

    val providers: List<String> get() = PROVIDER_REFERENCE.findAll(condition).map { it.groupValues[2] }.toList()

    fun conditionWith(rename: (String) -> String): String =
        PROVIDER_REFERENCE.replace(condition) { match -> match.groupValues[1] + rename(match.groupValues[2]) }

    companion object {
        const val TOKEN_SEPARATOR = ","
        const val RULE_SET = "RULE-SET"
        private val PROVIDER_REFERENCE = Regex("""(RULE-SET\s*,\s*)([^,()]+)""")
    }
}

enum class GeoList(val prefix: String) {
    GEOIP("geoip"),
    GEOSITE("geosite");

    fun providerName(code: String): String = "$prefix-${code.lowercase()}"

    companion object {
        fun of(providerName: String): Pair<GeoList, String>? = entries.firstNotNullOfOrNull { list ->
            providerName.takeIf { it.startsWith(list.prefix + "-") }?.let { list to it.removePrefix(list.prefix + "-") }
        }
    }
}

data class ManagedList(val url: String, val kind: RuleSetKind) {
    val source: RuleListSource get() = RuleListSource(url, kind)
}

data class SubscriptionProvider(val name: String, val definition: JsonObject) {
    val managed: ManagedList?
        get() {
            if (text(TYPE) != HTTP) return null
            val url = (definition[URL] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            RuleSetFormat.fromWire(text(FORMAT)) ?: return null
            val kind = RuleSetKind.fromBehavior(text(BEHAVIOR)) ?: return null
            return ManagedList(url, kind)
        }

    private fun text(key: String): String? = (definition[key] as? JsonPrimitive)?.contentOrNull?.lowercase()

    companion object {
        const val TYPE = "type"
        const val HTTP = "http"
        const val URL = "url"
        const val FORMAT = "format"
        const val BEHAVIOR = "behavior"
    }
}

data class SkippedRule(val text: String, val reason: SkipReason)

data class SubscriptionRules(
    val rules: List<SubscriptionRule>,
    val providers: List<SubscriptionProvider>,
    val skipped: List<SkippedRule>
) {
    val isEmpty: Boolean get() = rules.isEmpty() && skipped.isEmpty()

    val managedLists: List<ManagedList> get() = providers.mapNotNull { it.managed }.distinct()

    fun available(isInstalled: (ManagedList) -> Boolean): SubscriptionRules {
        val missing = providers.filter { provider -> provider.managed?.let { !isInstalled(it) } == true }.map { it.name }.toSet()
        if (missing.isEmpty()) return this
        return copy(
            rules = rules.filter { rule -> rule.providers.none { it in missing } },
            providers = providers.filter { it.name !in missing }
        )
    }

    fun isWaiting(rule: SubscriptionRule, waitingUrls: Set<String>): Boolean =
        rule.providers.any { name -> providers.firstOrNull { it.name == name }?.managed?.url in waitingUrls }

    fun encode(): String = JsonObject(
        mapOf(
            RULES to JsonArray(rules.map { rule ->
                JsonObject(
                    mapOf(
                        CONDITION to JsonPrimitive(rule.condition),
                        OPTIONS to JsonArray(rule.options.map(::JsonPrimitive)),
                        TARGET to JsonPrimitive(rule.target.wire)
                    )
                )
            }),
            PROVIDERS to JsonObject(providers.associate { it.name to it.definition }),
            SKIPPED to JsonArray(skipped.map { item ->
                JsonObject(mapOf(TEXT to JsonPrimitive(item.text), REASON to JsonPrimitive(item.reason.wire)))
            })
        )
    ).toString()

    companion object {
        private const val RULES = "rules"
        private const val PROVIDERS = "providers"
        private const val SKIPPED = "skipped"
        private const val CONDITION = "condition"
        private const val OPTIONS = "options"
        private const val TARGET = "target"
        private const val TEXT = "text"
        private const val REASON = "reason"

        fun providerName(groupId: Long, name: String): String = "sub$groupId-$name"

        fun decode(json: String?): SubscriptionRules? {
            if (json == null) return null
            return runCatching {
                val root = Json.parseToJsonElement(json).jsonObject
                SubscriptionRules(
                    rules = root.getValue(RULES).jsonArray.mapNotNull { element ->
                        val item = element.jsonObject
                        SubscriptionRule(
                            condition = item.getValue(CONDITION).jsonPrimitive.content,
                            options = item.getValue(OPTIONS).jsonArray.map { it.jsonPrimitive.content },
                            target = SubscriptionTarget.fromWire(item.getValue(TARGET).jsonPrimitive.contentOrNull) ?: return@mapNotNull null
                        )
                    },
                    providers = root.getValue(PROVIDERS).jsonObject.map { SubscriptionProvider(it.key, it.value.jsonObject) },
                    skipped = root.getValue(SKIPPED).jsonArray.mapNotNull { element ->
                        val item = element.jsonObject
                        SkippedRule(
                            text = item.getValue(TEXT).jsonPrimitive.content,
                            reason = SkipReason.fromWire(item.getValue(REASON).jsonPrimitive.contentOrNull) ?: return@mapNotNull null
                        )
                    }
                )
            }.getOrNull()
        }
    }
}

data class AppliedSubscriptionRules(val groupId: Long, val rules: SubscriptionRules) {
    fun available(isInstalled: (ManagedList) -> Boolean): AppliedSubscriptionRules = copy(rules = rules.available(isInstalled))
}
