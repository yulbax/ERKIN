package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.data.profile.GeoList
import io.github.yulbax.erkin.data.profile.SkipReason
import io.github.yulbax.erkin.data.profile.SkippedRule
import io.github.yulbax.erkin.data.profile.SubscriptionProvider
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull

internal object ClashRulesReader {
    private const val SEPARATOR = ','
    private const val MATCH = "MATCH"
    private const val RULE_SET = "RULE-SET"
    private const val PROVIDER_HTTP = "http"
    private const val PROVIDER_INLINE = "inline"
    private val DROPPED_PROVIDER_KEYS = setOf("path", "proxy")
    private val COMPOSITE = setOf("AND", "OR", "NOT")
    private val GEODATA = setOf("IP-ASN", "SRC-GEOIP", "SRC-IP-ASN")
    private val SUPPORTED = setOf(
        "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-REGEX", "DOMAIN-WILDCARD",
        "IP-CIDR", "IP-CIDR6", "IP-SUFFIX", "SRC-IP-CIDR", "SRC-IP-SUFFIX",
        "DST-PORT", "SRC-PORT", "IN-PORT", "IN-TYPE", "NETWORK", "UID", "DSCP",
        "PROCESS-NAME", "PROCESS-NAME-REGEX", "PROCESS-PATH", "PROCESS-PATH-REGEX",
        RULE_SET
    ) + COMPOSITE
    private val NESTED_TYPE = Regex("""\(\s*([A-Za-z0-9-]+)\s*,""")
    private val NESTED_GEO = Regex("""\(\s*(GEOIP|GEOSITE)\s*,\s*([^,()\s]+)([^()]*)\)""", RegexOption.IGNORE_CASE)
    private val GEO_CODE = Regex("""[A-Za-z0-9._@!-]+""")
    private const val LAN = "lan"
    private const val PRIVATE = "private"

    fun read(text: String): SubscriptionRules? {
        val root = ClashYamlDocument.load(text) as? JsonObject ?: return null
        val lines = (root["rules"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?: return null
        val providers = providers(root["rule-providers"] as? JsonObject)
        val targets = TargetResolver(root)
        val geo = GeoProviders(providers.map { it.name }.toSet())
        val rules = mutableListOf<SubscriptionRule>()
        val skipped = mutableListOf<SkippedRule>()
        lines.forEach { line ->
            when (val parsed = parse(line, geo, targets)) {
                Parsed.Dropped -> Unit
                is Parsed.Kept -> rules += parsed.rule
                is Parsed.Skipped -> skipped += SkippedRule(line, parsed.reason)
            }
        }
        val used = rules.flatMap { it.providers }.toSet()
        return SubscriptionRules(rules, (providers + geo.providers).filter { it.name in used }, skipped).takeUnless { it.isEmpty }
    }

    private sealed interface Parsed {
        data object Dropped : Parsed
        data class Kept(val rule: SubscriptionRule) : Parsed
        data class Skipped(val reason: SkipReason) : Parsed
    }

    private fun parse(line: String, geo: GeoProviders, targets: TargetResolver): Parsed {
        val tokens = splitTopLevel(line)
        val type = tokens.first().uppercase()
        if (type == MATCH) return Parsed.Dropped
        val conditionSize = 2
        if (tokens.size < conditionSize + 1) return Parsed.Skipped(SkipReason.UNSUPPORTED)
        val written = tokens.take(conditionSize).joinToString(SEPARATOR.toString())
        val condition = geo.rewrite(type, written) ?: return Parsed.Skipped(SkipReason.UNSUPPORTED)
        val conditionType = condition.substringBefore(SEPARATOR).uppercase()
        unsupportedReason(conditionType, condition.substringAfter(SEPARATOR))?.let { return Parsed.Skipped(it) }
        val rule = SubscriptionRule(condition, tokens.drop(conditionSize + 1), SubscriptionTarget.VPN)
        if (rule.providers.any { it !in geo.known }) return Parsed.Skipped(SkipReason.NO_PROVIDER)
        val target = targets.resolve(tokens[conditionSize]) ?: return Parsed.Skipped(SkipReason.NO_TARGET)
        return Parsed.Kept(rule.copy(target = target))
    }

    private fun unsupportedReason(type: String, argument: String): SkipReason? {
        val types = if (type in COMPOSITE) listOf(type) + NESTED_TYPE.findAll(argument).map { it.groupValues[1].uppercase() } else listOf(type)
        return when {
            types.any { it in GEODATA } -> SkipReason.GEODATA
            types.any { it !in SUPPORTED } -> SkipReason.UNSUPPORTED
            else -> null
        }
    }

    private class GeoProviders(private val declared: Set<String>) {
        private val created = linkedMapOf<Pair<GeoList, String>, SubscriptionProvider>()

        val providers: List<SubscriptionProvider> get() = created.values.toList()

        val known: Set<String> get() = declared + created.values.map { it.name }

        fun rewrite(type: String, condition: String): String? {
            val list = GeoList.entries.firstOrNull { it.name == type }
            return when {
                list != null -> provider(list, condition.substringAfter(SEPARATOR).trim())?.let { "$RULE_SET$SEPARATOR$it" }
                type in COMPOSITE -> rewriteNested(condition)
                else -> condition
            }
        }

        private fun rewriteNested(condition: String): String? {
            var failed = false
            val rewritten = NESTED_GEO.replace(condition) { match ->
                val list = GeoList.valueOf(match.groupValues[1].uppercase())
                val name = provider(list, match.groupValues[2])
                if (name == null) failed = true
                "($RULE_SET$SEPARATOR${name.orEmpty()}${match.groupValues[3]})"
            }
            return rewritten.takeUnless { failed }
        }

        private fun provider(list: GeoList, code: String): String? {
            if (!GEO_CODE.matches(code)) return null
            val value = if (list == GeoList.GEOIP && code.equals(LAN, ignoreCase = true)) PRIVATE else code
            val key = list to value.lowercase()
            created[key]?.let { return it.name }
            val name = uniqueName(list.providerName(value))
            val source = if (list == GeoList.GEOIP) RuleLists.country(value) else RuleLists.geoSite(value)
            created[key] = SubscriptionProvider(name, definition(source))
            return name
        }

        private fun uniqueName(base: String): String {
            val taken = known
            return generateSequence(0) { it + 1 }.map { if (it == 0) base else "$base-$it" }.first { it !in taken }
        }

        private fun definition(source: RuleListSource): JsonObject = buildJsonObject {
            put(SubscriptionProvider.TYPE, PROVIDER_HTTP)
            put(SubscriptionProvider.URL, source.url)
            put(SubscriptionProvider.BEHAVIOR, source.kind?.behavior.orEmpty())
            put(SubscriptionProvider.FORMAT, RuleSetFormat.MRS.wire)
        }
    }

    private fun splitTopLevel(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        line.forEach { char ->
            when {
                char == '(' -> depth++
                char == ')' -> depth--
            }
            if (char == SEPARATOR && depth <= 0) {
                tokens += current.toString().trim()
                current.clear()
            } else {
                current.append(char)
            }
        }
        tokens += current.toString().trim()
        return tokens
    }

    private fun providers(definitions: JsonObject?): List<SubscriptionProvider> =
        definitions.orEmpty().mapNotNull { (name, element) ->
            val definition = element as? JsonObject ?: return@mapNotNull null
            val keep = when ((definition["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
                PROVIDER_HTTP -> (definition["url"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true
                PROVIDER_INLINE -> definition["payload"] is JsonArray
                else -> false
            }
            if (keep) SubscriptionProvider(name, JsonObject(definition.filterKeys { it !in DROPPED_PROVIDER_KEYS })) else null
        }

    private class TargetResolver(root: JsonObject) {
        private val proxies = (root["proxies"] as? JsonArray).orEmpty().mapNotNull { name(it as? JsonObject) }.toSet()
        private val groups = (root["proxy-groups"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { group -> name(group)?.let { it to group } }
            .toMap()

        fun resolve(name: String, visited: Set<String> = emptySet()): SubscriptionTarget? {
            BUILT_IN[name]?.let { return it }
            if (name in proxies) return SubscriptionTarget.VPN
            if (name in visited) return null
            val group = groups[name] ?: return null
            return when ((group["type"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
                "select" -> firstMember(group)?.let { resolve(it, visited + name) }
                    ?: SubscriptionTarget.VPN.takeIf { firstMember(group) == null && providesMembers(group) }
                "url-test", "fallback", "load-balance" -> SubscriptionTarget.VPN
                else -> null
            }
        }

        private fun firstMember(group: JsonObject): String? =
            ((group["proxies"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull

        private fun providesMembers(group: JsonObject): Boolean =
            group.keys.any { it == "use" || it.startsWith("include-all") }

        private fun name(item: JsonObject?): String? = (item?.get("name") as? JsonPrimitive)?.contentOrNull

        private companion object {
            val BUILT_IN = mapOf(
                "DIRECT" to SubscriptionTarget.DIRECT,
                "REJECT" to SubscriptionTarget.BLOCK,
                "REJECT-DROP" to SubscriptionTarget.BLOCK
            )
        }
    }
}
