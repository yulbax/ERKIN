package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.AnyTls
import io.github.yulbax.erkin.proxy.protocol.CoreOnlyHandler
import io.github.yulbax.erkin.proxy.protocol.Http
import io.github.yulbax.erkin.proxy.protocol.Https
import io.github.yulbax.erkin.proxy.protocol.Hysteria
import io.github.yulbax.erkin.proxy.protocol.Hysteria2
import io.github.yulbax.erkin.proxy.protocol.Mieru
import io.github.yulbax.erkin.proxy.protocol.OpenVpn
import io.github.yulbax.erkin.proxy.protocol.Shadowsocks
import io.github.yulbax.erkin.proxy.protocol.Socks
import io.github.yulbax.erkin.proxy.protocol.Ssh
import io.github.yulbax.erkin.proxy.protocol.Ssr
import io.github.yulbax.erkin.proxy.protocol.Trojan
import io.github.yulbax.erkin.proxy.protocol.Tuic
import io.github.yulbax.erkin.proxy.protocol.Vless
import io.github.yulbax.erkin.proxy.protocol.Vmess
import io.github.yulbax.erkin.proxy.protocol.wireguard.AmneziaWg
import io.github.yulbax.erkin.proxy.protocol.wireguard.WireGuard
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

enum class ProxyProtocol(val wire: String, val handler: ProtocolHandler) {
    VMESS("vmess", Vmess),
    VLESS("vless", Vless),
    TROJAN("trojan", Trojan),
    SHADOWSOCKS("shadowsocks", Shadowsocks),
    HYSTERIA2("hysteria2", Hysteria2),
    AMNEZIAWG("amneziawg", AmneziaWg),
    WIREGUARD("wireguard", WireGuard),
    TUIC("tuic", Tuic),
    ANYTLS("anytls", AnyTls),
    SOCKS5("socks5", Socks),
    HTTPS("https", Https),
    HTTP("http", Http),
    HYSTERIA("hysteria", Hysteria),
    SSR("ssr", Ssr),
    MIERU("mieru", Mieru),
    SSH("ssh", Ssh),
    OPENVPN("openvpn", OpenVpn),
    SNELL("snell", CoreOnlyHandler("snell")),
    SUDOKU("sudoku", CoreOnlyHandler("sudoku")),
    MASQUE("masque", CoreOnlyHandler("masque")),
    TRUSTTUNNEL("trusttunnel", CoreOnlyHandler("trusttunnel")),
    SHADOWQUIC("shadowquic", CoreOnlyHandler("shadowquic")),
    GOST_RELAY("gost-relay", CoreOnlyHandler("gost-relay"));

    fun engineProxy(tag: String, descriptor: String): EngineProxy {
        val parsed = descriptorJson.parseToJsonElement(descriptor) as JsonObject
        val engine = handler.engineDescriptor(parsed)
        return EngineProxy(tag, if (engine == parsed) descriptor else engine.toString(), handler.requirements(parsed))
    }

    companion object {
        val allSchemes: List<String> get() =
            entries.flatMap { (it.handler as? LinkHandler)?.schemes.orEmpty() } + CoreProxySource.SCHEME

        fun ofCore(proxy: JsonObject): Pair<ProxyProtocol, JsonObject>? =
            entries.firstNotNullOfOrNull { protocol -> protocol.handler.normalize(proxy)?.let { protocol to it } }

        fun fromWire(value: String): ProxyProtocol? = entries.firstOrNull { it.wire == value }
    }
}

private val FOREIGN_KEYS = setOf("name", "dialer-proxy", "interface-name", "routing-mark")
private const val MAX_PORT = 65535

interface ProtocolHandler {
    val coreType: String

    fun portOf(proxy: JsonObject): Int? = (proxy["port"] as? JsonPrimitive)?.intOrNull

    fun normalize(proxy: JsonObject): JsonObject? {
        val type = (proxy["type"] as? JsonPrimitive)?.contentOrNull
        val server = (proxy["server"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val port = portOf(proxy)
        val usable = type == coreType && !server.isNullOrBlank() && port != null && port in 1..MAX_PORT
        return if (usable) JsonObject(proxy - FOREIGN_KEYS) else null
    }

    fun server(descriptor: JsonObject): ServerAddress? {
        val host = (descriptor["server"] as? JsonPrimitive)?.contentOrNull ?: return null
        val port = portOf(descriptor) ?: return null
        return ServerAddress(host, port)
    }

    fun requirements(descriptor: JsonObject): Set<EngineRequirement>

    fun engineDescriptor(descriptor: JsonObject): JsonObject = descriptor
}

interface LinkHandler : ProtocolHandler {
    val schemes: List<String>

    fun recognizes(link: ShareLink): Boolean = link.scheme in schemes

    fun parse(link: ShareLink): ParsedLink
}

interface DocumentHandler : ProtocolHandler {
    fun recognizes(text: String): Boolean

    fun parse(text: String): ParsedLink
}

data class ParsedLink(val name: String, val proxy: JsonObject)

data class ServerAddress(val host: String, val port: Int)

data class ParsedProfile(
    val name: String,
    val protocol: ProxyProtocol,
    val proxy: JsonObject,
    val link: String
) {
    fun proxyJson(): String = descriptorJson.encodeToString(JsonObject.serializer(), proxy)
}

internal val descriptorJson = Json { ignoreUnknownKeys = true }
