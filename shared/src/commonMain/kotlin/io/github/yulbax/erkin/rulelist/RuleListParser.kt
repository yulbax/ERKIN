package io.github.yulbax.erkin.rulelist

import io.github.yulbax.erkin.vpn.core.RuleValues

data class ParsedRuleList(val title: String?, val domains: List<String>, val cidrs: List<String>) {
    val size: Int get() = domains.size + cidrs.size
}

object RuleListParser {
    private val TITLE = Regex("^[#!]\\s*Title:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
    private val COSMETIC_MARKERS = listOf("##", "#@#", "#?#", "#$#", "#%#")
    private val COMMENT_STARTS = setOf('#', '!', ';', '[')
    private val HOSTS_SINKS = setOf("0.0.0.0", "127.0.0.1", "::", "::0", "::1")
    private val IGNORED_HOSTS = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback")
    private val NEUTRAL_MODIFIERS = setOf("important", "all", "document")
    private val WILDCARD_PREFIXES = listOf("+.", "*.", ".")
    private const val SUFFIX_PREFIX = "+."

    private sealed interface Entry {
        data class Domain(val value: String) : Entry
        data class Cidr(val value: String) : Entry
    }

    fun parse(lines: Sequence<String>): ParsedRuleList {
        var title: String? = null
        val domains = LinkedHashSet<String>()
        val cidrs = LinkedHashSet<String>()
        lines.forEach { raw ->
            val line = raw.trim()
            if (title == null) title = TITLE.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            when (val entry = entry(line)) {
                is Entry.Domain -> domains += entry.value
                is Entry.Cidr -> cidrs += entry.value
                null -> Unit
            }
        }
        return ParsedRuleList(title, domains.toList(), cidrs.toList())
    }

    private fun entry(line: String): Entry? {
        if (line.isEmpty() || line[0] in COMMENT_STARTS || line.startsWith("//") || line.startsWith("@@")) return null
        if (COSMETIC_MARKERS.any { it in line }) return null
        val text = line.substringBefore('#').trim().removePrefix("- ").trim().trim('\'', '"')
        return when {
            text.isEmpty() -> null
            text.startsWith("||") -> adblock(text.removePrefix("||"))
            ',' in text -> classical(text)
            else -> plain(text)
        }
    }

    private fun adblock(body: String): Entry? {
        val modifiers = body.substringAfter('$', "")
        if (modifiers.isNotEmpty() && modifiers.split(',').any { it.trim() !in NEUTRAL_MODIFIERS }) return null
        val host = body.substringBefore('$').removeSuffix("|").removeSuffix("^")
        if (host.any { it in "/*^|:" }) return null
        return suffix(host)
    }

    private fun classical(text: String): Entry? {
        val parts = text.split(',').map { it.trim() }
        val value = parts.getOrNull(1) ?: return null
        return when (parts[0].uppercase()) {
            "DOMAIN-SUFFIX" -> suffix(value)
            "DOMAIN" -> domain(value)?.let(Entry::Domain)
            "IP-CIDR", "IP-CIDR6" -> cidr(value)
            else -> null
        }
    }

    private fun plain(text: String): Entry? {
        val tokens = text.split(WHITESPACE)
        val value = when {
            tokens.size == 1 -> tokens[0]
            tokens[0] in HOSTS_SINKS -> tokens[1]
            else -> return null
        }
        if (value.lowercase() in IGNORED_HOSTS || value in HOSTS_SINKS) return null
        return cidr(value) ?: suffix(value)
    }

    private fun suffix(raw: String): Entry? {
        val host = WILDCARD_PREFIXES.firstOrNull { raw.startsWith(it) }?.let { raw.removePrefix(it) } ?: raw
        return domain(host)?.let { Entry.Domain(SUFFIX_PREFIX + it) }
    }

    private fun domain(raw: String): String? =
        raw.takeUnless { "://" in it }?.let(RuleValues.Domain::normalize)?.takeIf { '.' in it }

    private fun cidr(raw: String): Entry? =
        raw.takeIf { it.firstOrNull()?.isDigit() == true || ':' in it }?.let(RuleValues.Cidr::normalize)?.let(Entry::Cidr)
}
