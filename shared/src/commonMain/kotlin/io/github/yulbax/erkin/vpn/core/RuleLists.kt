package io.github.yulbax.erkin.vpn.core

import java.security.MessageDigest
import java.util.Locale

enum class RuleSetKind(val behavior: String) {
    DOMAIN("domain"),
    IPCIDR("ipcidr");

    companion object {
        fun fromBehavior(value: String): RuleSetKind? = entries.firstOrNull { it.behavior == value }
    }
}

enum class RuleSetFormat(val wire: String) {
    TEXT("text"),
    MRS("mrs")
}

data class RuleListSource(val url: String, val kind: RuleSetKind?)

object RuleLists {
    const val DIRECTORY = "lists"
    private const val KEY_BYTES = 8
    private const val EXTENSION = "mrs"
    private const val COUNTRY_URL = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/%s.mrs"

    fun source(rule: RoutingRule): RuleListSource? = source(rule.match, rule.value)

    fun source(match: RuleMatch, value: String): RuleListSource? = when (match) {
        RuleMatch.LIST -> RuleListSource(value, null)
        RuleMatch.COUNTRY -> RuleListSource(COUNTRY_URL.format(value.lowercase(Locale.ROOT)), RuleSetKind.IPCIDR)
        RuleMatch.DOMAIN_SUFFIX, RuleMatch.DOMAIN, RuleMatch.DOMAIN_KEYWORD, RuleMatch.IP_CIDR -> null
    }

    fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(KEY_BYTES).joinToString("") { "%02x".format(it) }

    fun fileName(url: String, kind: RuleSetKind): String = "${key(url)}.${kind.behavior}.$EXTENSION"

    fun path(url: String, kind: RuleSetKind): String = "$DIRECTORY/${fileName(url, kind)}"

    fun providerName(url: String, kind: RuleSetKind): String = "list-${key(url)}-${kind.behavior}"
}
