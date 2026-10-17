package io.github.yulbax.erkin.vpn.core

import java.security.MessageDigest
import java.util.Locale

enum class RuleSetKind(val behavior: String) {
    DOMAIN("domain"),
    IPCIDR("ipcidr");

    companion object {
        fun fromBehavior(value: String?): RuleSetKind? = entries.firstOrNull { it.behavior == value }
    }
}

enum class RuleSetFormat(val wire: String) {
    TEXT("text"),
    MRS("mrs")
}

data class RuleListSource(val url: String, val kind: RuleSetKind?)

object RuleLists {
    const val DIRECTORY = "lists"
    const val COUNTRY_URL_PREFIX = "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/"
    const val COUNTRY_URL_SUFFIX = ".mrs"
    private const val KEY_BYTES = 8
    private const val EXTENSION = "mrs"

    fun url(rule: RoutingRule): String? = when (rule.match) {
        RuleMatch.RULE_SET -> rule.value
        RuleMatch.DOMAIN_SUFFIX, RuleMatch.DOMAIN, RuleMatch.DOMAIN_KEYWORD, RuleMatch.IP_CIDR -> null
    }

    fun country(code: String): RuleListSource =
        RuleListSource(COUNTRY_URL_PREFIX + code.lowercase(Locale.ROOT) + COUNTRY_URL_SUFFIX, RuleSetKind.IPCIDR)

    fun countryOf(url: String): String? =
        url.takeIf { it.startsWith(COUNTRY_URL_PREFIX) && it.endsWith(COUNTRY_URL_SUFFIX) }
            ?.removePrefix(COUNTRY_URL_PREFIX)?.removeSuffix(COUNTRY_URL_SUFFIX)
            ?.let(RuleValues.Country::normalize)

    fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(KEY_BYTES).joinToString("") { "%02x".format(it) }

    fun fileName(url: String, kind: RuleSetKind): String = "${key(url)}.${kind.behavior}.$EXTENSION"

    fun path(url: String, kind: RuleSetKind): String = "$DIRECTORY/${fileName(url, kind)}"

    fun providerName(url: String, kind: RuleSetKind): String = "list-${key(url)}-${kind.behavior}"
}
