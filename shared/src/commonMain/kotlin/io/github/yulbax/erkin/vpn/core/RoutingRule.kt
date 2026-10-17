package io.github.yulbax.erkin.vpn.core

import java.net.IDN
import java.net.InetAddress
import java.net.URI
import java.util.Locale

enum class RuleMatch(val wire: String, val mihomo: String, val format: RuleValueFormat) {
    DOMAIN_SUFFIX("suffix", "DOMAIN-SUFFIX", RuleValues.Domain),
    DOMAIN("domain", "DOMAIN", RuleValues.Domain),
    DOMAIN_KEYWORD("keyword", "DOMAIN-KEYWORD", RuleValues.Keyword),
    IP_CIDR("ip_cidr", "IP-CIDR", RuleValues.Cidr),
    RULE_SET("ruleset", "RULE-SET", RuleValues.Link);

    fun normalize(raw: String): String? = format.normalize(raw)

    fun display(value: String): String = format.display(value)

    companion object {
        fun fromWire(value: String): RuleMatch? = entries.firstOrNull { it.wire == value }
    }
}

interface RuleValueFormat {
    fun normalize(raw: String): String?

    fun display(value: String): String = value
}

enum class RuleTarget(val wire: String) {
    VPN("VPN"),
    BYEDPI("BYEDPI"),
    DIRECT("DIRECT"),
    BLOCK("BLOCK");

    companion object {
        fun fromWire(value: String): RuleTarget? = entries.firstOrNull { it.wire == value }
    }
}

data class RoutingRule(val match: RuleMatch, val value: String, val target: RuleTarget)

object RuleValues {
    private const val MAX_DOMAIN_LENGTH = 253
    private val DOMAIN_LABELS = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)*$")
    private val KEYWORD = Regex("^[a-z0-9._-]+$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6 = Regex("^[0-9a-f:.]*:[0-9a-f:.]*$")

    object Domain : RuleValueFormat {
        override fun normalize(raw: String): String? {
            val input = raw.trim().lowercase()
            val host = if ("://" in input) runCatching { URI(input).host }.getOrNull() ?: return null else input.substringBefore('/')
            val trimmed = host.removePrefix("*.").trim('.')
            if (trimmed.isEmpty()) return null
            val ascii = runCatching { IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase() ?: return null
            return ascii.takeIf { it.length <= MAX_DOMAIN_LENGTH && DOMAIN_LABELS.matches(it) }
        }

        override fun display(value: String): String = runCatching { IDN.toUnicode(value) }.getOrDefault(value)
    }

    object Keyword : RuleValueFormat {
        override fun normalize(raw: String): String? =
            raw.trim().lowercase().takeIf { it.length <= MAX_DOMAIN_LENGTH && KEYWORD.matches(it) }
    }

    object Cidr : RuleValueFormat {
        override fun normalize(raw: String): String? {
            val input = raw.trim().lowercase()
            val address = input.substringBefore('/')
            val v4 = IPV4.matches(address)
            if (!v4 && !IPV6.matches(address)) return null
            if (v4 && address.split('.').any { it.toInt() > 255 }) return null
            val maxPrefix = if (v4) 32 else 128
            val prefix = if ('/' in input) input.substringAfter('/').toIntOrNull() ?: return null else maxPrefix
            if (prefix !in 0..maxPrefix) return null
            val canonical = runCatching { InetAddress.getByName(address).hostAddress }.getOrNull() ?: return null
            return "$canonical/$prefix"
        }
    }

    object Link : RuleValueFormat {
        private val SCHEMES = setOf("http", "https")

        override fun normalize(raw: String): String? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES || uri.host.isNullOrEmpty()) return null
            return uri.toString()
        }
    }

    object Country : RuleValueFormat {
        val CODES: Set<String> = Locale.getISOCountries().toSet()

        override fun normalize(raw: String): String? = raw.trim().uppercase().takeIf { it in CODES }
    }
}
