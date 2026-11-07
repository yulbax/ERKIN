package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.protocol.decodeComponent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object AmneziaConfigLink {
    const val SCHEME = "amneziawg"
    private const val MAX_PAYLOAD_BYTES = 1 shl 20

    fun parse(link: String): ParsedLink {
        val body = link.removePrefix("$SCHEME://")
        val encoded = body.substringBefore('#')
            .filterNot { it.isWhitespace() }
            .replace('+', '-')
            .replace('/', '_')
            .let { it.padEnd((it.length + 3) / 4 * 4, '=') }
        val raw = Base64.UrlSafe.decode(encoded)
        require(raw.size in 1..MAX_PAYLOAD_BYTES) { "unexpected payload size ${raw.size}" }

        val config = WgIni.parse(raw.decodeToString())
        val name = body.substringAfter('#', "").trim().takeIf { it.isNotEmpty() }?.let(::decodeComponent)
            ?: config.host
        return ParsedLink(name, AmneziaWg.descriptor(config))
    }
}
