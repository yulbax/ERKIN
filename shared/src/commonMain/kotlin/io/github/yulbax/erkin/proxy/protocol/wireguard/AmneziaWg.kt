package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AmneziaWg : ProtocolHandler {
    override val schemes: List<String> = listOf(AmneziaVpnLink.SCHEME)

    private const val VERSION_3 = 3
    private val OBFUSCATION_NUMBERS = listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4")
    private val OBFUSCATION_TEXT = listOf("h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5").associateWith { it }
    private val VERSION_3_TEXT = mapOf(
        "headerprotectionkey" to "header-protection-key",
        "contentpaddingaddition" to "content-padding-addition",
        "rekeyaftertime" to "rekey-after-time",
        "rekeytimeout" to "rekey-timeout",
        "rejectaftertime" to "reject-after-time",
        "keepalivetimeout" to "keepalive-timeout",
        "maxhandshakeattempts" to "max-handshake-attempts"
    )
    private val VERSION_3_FLAGS = mapOf(
        "randomtrailers" to "random-trailers",
        "disablecookies" to "disable-cookies"
    )
    private val OBFUSCATION_KEYS: Set<String> =
        OBFUSCATION_NUMBERS.toSet() + OBFUSCATION_TEXT.keys + VERSION_3_TEXT.keys + VERSION_3_FLAGS.keys

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

    internal fun descriptor(config: WgConfig): JsonObject = wgProxy(config, options(config.interfaceValues))

    private fun options(values: Map<String, String>): JsonObject = buildJsonObject {
        val present = values.filterValues { it.isNotEmpty() }
        if (present.keys.any { it in VERSION_3_TEXT || it in VERSION_3_FLAGS }) put("version", VERSION_3)
        OBFUSCATION_NUMBERS.forEach { key -> present[key]?.toLongOrNull()?.let { put(key, it) } }
        (OBFUSCATION_TEXT + VERSION_3_TEXT).forEach { (key, option) -> present[key]?.let { put(option, it) } }
        VERSION_3_FLAGS.forEach { (key, option) -> present[key]?.let { put(option, it.isEnabled()) } }
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> =
        if (descriptor.hasIpv6Address()) emptySet() else setOf(EngineRequirement.IPV4_ONLY)

    private fun String.isEnabled(): Boolean =
        equals("on", ignoreCase = true) || equals("true", ignoreCase = true) || this == "1"
}
