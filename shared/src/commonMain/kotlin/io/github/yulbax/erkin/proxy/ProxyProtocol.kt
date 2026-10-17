package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.AnyTls
import io.github.yulbax.erkin.proxy.protocol.Https
import io.github.yulbax.erkin.proxy.protocol.Hysteria2
import io.github.yulbax.erkin.proxy.protocol.OpenVpn
import io.github.yulbax.erkin.proxy.protocol.Shadowsocks
import io.github.yulbax.erkin.proxy.protocol.Socks
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
    OPENVPN("openvpn", OpenVpn);

    fun engineProxy(tag: String, descriptor: String): EngineProxy {
        val parsed = descriptorJson.parseToJsonElement(descriptor) as JsonObject
        return EngineProxy(tag, descriptor, handler.requirements(parsed))
    }

    companion object {
        val allSchemes: List<String> get() = entries.flatMap { it.handler.schemes }

        fun fromWire(value: String): ProxyProtocol? = entries.firstOrNull { it.wire == value }
    }
}

interface ProtocolHandler {
    val schemes: List<String>

    fun recognizes(input: String): Boolean = schemes.any { input.startsWith("$it://") }

    fun parse(input: String): ParsedLink

    fun server(descriptor: JsonObject): ServerAddress? {
        val host = (descriptor["server"] as? JsonPrimitive)?.contentOrNull ?: return null
        val port = (descriptor["port"] as? JsonPrimitive)?.intOrNull ?: return null
        return ServerAddress(host, port)
    }

    fun requirements(descriptor: JsonObject): Set<EngineRequirement>
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
