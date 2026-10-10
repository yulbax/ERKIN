package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.proxy.ServerAddress
import io.github.yulbax.erkin.vpn.core.EnginePlacement
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

object AmneziaWg : ProtocolHandler {
    override val schemes: List<String> = listOf(AmneziaVpnLink.SCHEME)

    override val placement: EnginePlacement = EnginePlacement.ENDPOINT

    private val OBFUSCATION_NUMBERS = listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4")
    private val OBFUSCATION_RANGES = listOf("h1", "h2", "h3", "h4").associateWith { it } + mapOf(
        "contentpaddingaddition" to "content_padding_addition",
        "rekeyaftertime" to "rekey_after_time",
        "rekeytimeout" to "rekey_timeout",
        "rejectaftertime" to "reject_after_time",
        "keepalivetimeout" to "keepalive_timeout",
        "maxhandshakeattempts" to "max_handshake_attempts"
    )
    private val OBFUSCATION_TEXT = mapOf(
        "i1" to "i1", "i2" to "i2", "i3" to "i3", "i4" to "i4", "i5" to "i5",
        "headerprotectionkey" to "header_protection_key"
    )
    private val OBFUSCATION_FLAGS = mapOf(
        "randomtrailers" to "random_trailers",
        "disablecookies" to "disable_cookies"
    )
    private val OBFUSCATION_KEYS: Set<String> =
        OBFUSCATION_NUMBERS.toSet() + OBFUSCATION_RANGES.keys + OBFUSCATION_TEXT.keys + OBFUSCATION_FLAGS.keys

    internal fun isAmnezia(config: WgConfig): Boolean = config.interfaceValues.keys.any { it in OBFUSCATION_KEYS }

    override fun recognizes(input: String): Boolean =
        super.recognizes(input) || WgIni.parseOrNull(input)?.let(::isAmnezia) == true

    override fun parse(input: String): ParsedLink =
        if (WgIni.looksLikeConfig(input)) {
            val config = WgIni.parse(input)
            ParsedLink(config.host, descriptor(config))
        } else {
            AmneziaVpnLink.parse(input)
        }

    internal fun descriptor(config: WgConfig): JsonObject = wgEndpoint(WgDialect.AMNEZIA, config) {
        val values = config.interfaceValues
        OBFUSCATION_NUMBERS.forEach { key -> values[key]?.toLongOrNull()?.let { put(key, it) } }
        OBFUSCATION_RANGES.forEach { (key, jsonName) -> values[key]?.let { put(jsonName, it) } }
        OBFUSCATION_TEXT.forEach { (key, jsonName) -> values[key]?.let { put(jsonName, it) } }
        OBFUSCATION_FLAGS.forEach { (key, jsonName) -> values[key]?.let { put(jsonName, it.isEnabled()) } }
    }

    override fun server(descriptor: JsonObject): ServerAddress? = peerServer(descriptor)

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> =
        if (descriptor.hasIpv6Address()) {
            setOf(EngineRequirement.RESOLVE_BEFORE_DIAL)
        } else {
            setOf(EngineRequirement.RESOLVE_BEFORE_DIAL, EngineRequirement.IPV4_ONLY)
        }

    private fun String.isEnabled(): Boolean =
        equals("on", ignoreCase = true) || equals("true", ignoreCase = true) || this == "1"
}
