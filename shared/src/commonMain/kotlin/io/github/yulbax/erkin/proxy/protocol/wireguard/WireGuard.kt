package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.DocumentHandler
import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.proxy.protocol.title
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import java.util.Base64

object WireGuard : LinkHandler, DocumentHandler {
    override val coreType = "wireguard"
    override val schemes: List<String> = listOf("wireguard")

    private const val DEFAULT_PORT = 51820
    private const val DEFAULT_MTU = 1420
    private const val DEFAULT_ADDRESS = "172.16.0.2/32"
    private const val KEY_BYTES = 32
    private val FULL_TUNNEL = listOf("0.0.0.0/0", "::/0")

    override fun normalize(proxy: JsonObject): JsonObject? =
        if (AMNEZIA_OPTION !in proxy && "private-key" in proxy) super<LinkHandler>.normalize(proxy) else null

    override fun recognizes(text: String): Boolean =
        WgIni.parseOrNull(text)?.let { !AmneziaWg.isAmnezia(it) } == true

    override fun parse(text: String): ParsedLink {
        val config = WgIni.parse(text)
        return ParsedLink(config.host, wgProxy(config))
    }

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val host = requireNotNull(link.host.takeIf { it.isNotEmpty() }) { "missing host" }
        val config = WgConfig(
            privateKey = requireKey(link.userInfo, "private key"),
            addresses = (q["address"]?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS)
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::withPrefix),
            mtu = q["mtu"]?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MTU,
            peers = listOf(
                WgPeer(
                    host = host,
                    port = link.port ?: DEFAULT_PORT,
                    publicKey = requireKey(q["publickey"], "publickey"),
                    presharedKey = q["presharedkey"]?.takeIf { it.isNotEmpty() }?.let { requireKey(it, "presharedkey") },
                    allowedIps = FULL_TUNNEL,
                    keepalive = null,
                    reserved = q["reserved"]?.let(WgIni::reservedBytes)
                )
            ),
            interfaceValues = emptyMap()
        )
        return ParsedLink(link.title, wgProxy(config))
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
