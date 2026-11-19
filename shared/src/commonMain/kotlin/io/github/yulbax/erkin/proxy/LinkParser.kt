package io.github.yulbax.erkin.proxy

object LinkParser {
    private val documentHandlers: List<Pair<ProxyProtocol, DocumentHandler>> =
        ProxyProtocol.entries.mapNotNull { protocol -> (protocol.handler as? DocumentHandler)?.let { protocol to it } }

    internal fun documentOwner(text: String): Pair<ProxyProtocol, DocumentHandler>? =
        documentHandlers.firstOrNull { (_, handler) -> handler.recognizes(text) }

    fun parse(source: String): ParsedProfile? {
        val input = source.trim()
        return runCatching { resolve(input) }.getOrNull()
    }

    private fun resolve(input: String): ParsedProfile? {
        documentOwner(input)?.let { (protocol, handler) ->
            val parsed = handler.parse(input)
            return ParsedProfile(parsed.name, protocol, parsed.proxy, input)
        }
        val link = ShareLink.parse(input) ?: return null
        if (link.scheme == CoreProxySource.SCHEME) return resolveCore(link, input)
        return ProxyProtocol.entries.firstNotNullOfOrNull { protocol ->
            val handler = protocol.handler as? LinkHandler
            handler?.takeIf { it.recognizes(link) }?.let { protocol to it }
        }?.let { (protocol, handler) ->
            val parsed = handler.parse(link)
            ParsedProfile(parsed.name, protocol, parsed.proxy, input)
        }
    }

    private fun resolveCore(link: ShareLink, input: String): ParsedProfile? {
        val proxy = CoreProxySource.decode(link) ?: return null
        val (protocol, normalized) = ProxyProtocol.ofCore(proxy) ?: return null
        val name = link.name ?: protocol.handler.server(normalized)?.host ?: return null
        return ParsedProfile(name, protocol, normalized, input)
    }
}
