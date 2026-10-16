package io.github.yulbax.erkin.vpn.core

import java.net.IDN
import java.net.InetAddress
import java.net.URI

enum class RuleMatch(val wire: String, val mihomo: String) {
    DOMAIN_SUFFIX("suffix", "DOMAIN-SUFFIX"),
    DOMAIN("domain", "DOMAIN"),
    DOMAIN_KEYWORD("keyword", "DOMAIN-KEYWORD"),
    IP_CIDR("ip_cidr", "IP-CIDR");

    companion object {
        fun fromWire(value: String): RuleMatch? = entries.firstOrNull { it.wire == value }
    }
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

object RoutingRules {
    private const val MAX_DOMAIN_LENGTH = 253
    private val DOMAIN_LABELS = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)*$")
    private val KEYWORD = Regex("^[a-z0-9._-]+$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6 = Regex("^[0-9a-f:.]*:[0-9a-f:.]*$")

    fun normalize(match: RuleMatch, raw: String): String? = when (match) {
        RuleMatch.DOMAIN_SUFFIX, RuleMatch.DOMAIN -> domain(raw)
        RuleMatch.DOMAIN_KEYWORD -> raw.trim().lowercase().takeIf { it.length <= MAX_DOMAIN_LENGTH && KEYWORD.matches(it) }
        RuleMatch.IP_CIDR -> cidr(raw)
    }

    fun display(match: RuleMatch, value: String): String = when (match) {
        RuleMatch.DOMAIN_SUFFIX, RuleMatch.DOMAIN -> runCatching { IDN.toUnicode(value) }.getOrDefault(value)
        else -> value
    }

    private fun domain(raw: String): String? {
        val input = raw.trim().lowercase()
        val host = if ("://" in input) runCatching { URI(input).host }.getOrNull() ?: return null else input.substringBefore('/')
        val trimmed = host.removePrefix("*.").trim('.')
        if (trimmed.isEmpty()) return null
        val ascii = runCatching { IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase() ?: return null
        return ascii.takeIf { it.length <= MAX_DOMAIN_LENGTH && DOMAIN_LABELS.matches(it) }
    }

    private fun cidr(raw: String): String? {
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
