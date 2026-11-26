package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    fun encode(): String = storage.encodeToString(
        StoredRules.serializer(),
        StoredRules(
            rules = rules.map { StoredRule(it.condition, it.options, it.target.wire) },
            providers = providers.associate { it.name to it.definition },
            skipped = skipped.map { StoredSkip(it.text, it.reason.wire) }
        )
    )

    companion object {
        private val storage = Json { ignoreUnknownKeys = true }

        fun providerName(groupId: Long, name: String): String = "sub$groupId-$name"

        fun decode(json: String?): SubscriptionRules? {
            if (json == null) return null
            val stored = runCatching { storage.decodeFromString(StoredRules.serializer(), json) }.getOrNull() ?: return null
            return SubscriptionRules(
                rules = stored.rules.mapNotNull { rule ->
                    SubscriptionTarget.fromWire(rule.target)?.let { SubscriptionRule(rule.condition, rule.options, it) }
                },
                providers = stored.providers.map { (name, definition) -> SubscriptionProvider(name, definition) },
                skipped = stored.skipped.mapNotNull { skip -> SkipReason.fromWire(skip.reason)?.let { SkippedRule(skip.text, it) } }
            )
        }
    }
}

@Serializable
private data class StoredRules(
    val rules: List<StoredRule>,
    val providers: Map<String, JsonObject>,
    val skipped: List<StoredSkip>
)

@Serializable
private data class StoredRule(val condition: String, val options: List<String>, val target: String)

@Serializable
private data class StoredSkip(val text: String, val reason: String)

data class AppliedSubscriptionRules(val groupId: Long, val rules: SubscriptionRules) {
    fun available(isInstalled: (ManagedList) -> Boolean): AppliedSubscriptionRules = copy(rules = rules.available(isInstalled))
}
