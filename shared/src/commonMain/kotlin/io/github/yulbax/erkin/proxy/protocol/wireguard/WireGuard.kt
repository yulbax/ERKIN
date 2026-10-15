package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.proxy.protocol.fragmentName
import io.github.yulbax.erkin.proxy.protocol.queryMap
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.util.Base64

object WireGuard : ProtocolHandler {
    override val schemes: List<String> = listOf("wireguard")

    private const val DEFAULT_PORT = 51820
    private const val DEFAULT_MTU = 1420
    private const val DEFAULT_ADDRESS = "172.16.0.2/32"
    private const val KEY_BYTES = 32
    private val FULL_TUNNEL = listOf("0.0.0.0/0", "::/0")

    override fun recognizes(input: String): Boolean =
        super.recognizes(input) || WgIni.parseOrNull(input)?.let { !AmneziaWg.isAmnezia(it) } == true

    override fun parse(input: String): ParsedLink =
        if (WgIni.looksLikeConfig(input)) {
            val config = WgIni.parse(input)
            ParsedLink(config.host, wgProxy(config))
        } else {
            parseLink(input)
        }

    private fun parseLink(input: String): ParsedLink {
        val uri = URI(input)
        val q = uri.queryMap()
        val host = requireNotNull(uri.host?.trim('[', ']')?.takeIf { it.isNotEmpty() }) { "missing host" }
        val config = WgConfig(
            privateKey = requireKey(uri.userInfo, "private key"),
            addresses = (q["address"]?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS)
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::withPrefix),
            mtu = q["mtu"]?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MTU,
            peers = listOf(
                WgPeer(
                    host = host,
                    port = uri.port.takeIf { it > 0 } ?: DEFAULT_PORT,
                    publicKey = requireKey(q["publickey"], "publickey"),
                    presharedKey = q["presharedkey"]?.takeIf { it.isNotEmpty() }?.let { requireKey(it, "presharedkey") },
                    allowedIps = FULL_TUNNEL,
                    keepalive = null,
                    reserved = q["reserved"]?.let(WgIni::reservedBytes)
                )
            ),
            interfaceValues = emptyMap()
        )
        return ParsedLink(uri.fragmentName(host), wgProxy(config))
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> =
        if (descriptor.hasIpv6Address()) emptySet() else setOf(EngineRequirement.IPV4_ONLY)

    private fun requireKey(value: String?, label: String): String {
        val key = requireNotNull(value?.trim()?.takeIf { it.isNotEmpty() }) { "missing $label" }
        val decoded = runCatching { Base64.getDecoder().decode(key) }.getOrNull()
        require(decoded?.size == KEY_BYTES) { "invalid $label" }
        return key
    }
}
