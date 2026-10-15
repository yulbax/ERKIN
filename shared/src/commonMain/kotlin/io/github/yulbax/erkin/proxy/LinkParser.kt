package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.decodeBase64Lenient

object LinkParser {
    fun parse(rawLink: String): ParsedProfile? {
        val input = rawLink.trim()
        val protocol = ProxyProtocol.entries.firstOrNull { it.handler.recognizes(input) } ?: return null
        val parsed = runCatching { protocol.handler.parse(input) }.getOrNull() ?: return null
        return ParsedProfile(parsed.name, protocol, parsed.proxy, input)
    }
}

object SubscriptionParser {
    fun parseBody(body: String): List<ParsedProfile> {
        val text = if (body.contains("://")) body else decodeBase64Lenient(body) ?: body
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { LinkParser.parse(it) }
            .toList()
    }
}
