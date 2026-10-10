package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.Hysteria2
import io.github.yulbax.erkin.proxy.protocol.Shadowsocks
import io.github.yulbax.erkin.proxy.protocol.Trojan
import io.github.yulbax.erkin.proxy.protocol.Vless
import io.github.yulbax.erkin.proxy.protocol.Vmess
import io.github.yulbax.erkin.proxy.protocol.wireguard.AmneziaWg
import io.github.yulbax.erkin.proxy.protocol.wireguard.WireGuard
import io.github.yulbax.erkin.vpn.core.EnginePlacement
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

enum class ProxyProtocol(val wire: String, val handler: ProtocolHandler) {
    VMESS("vmess", Vmess),
    VLESS("vless", Vless),
    TROJAN("trojan", Trojan),
    SHADOWSOCKS("shadowsocks", Shadowsocks),
    HYSTERIA2("hysteria2", Hysteria2),
    AMNEZIAWG("amneziawg", AmneziaWg),
    WIREGUARD("wireguard", WireGuard);

    fun engineProxy(tag: String, descriptor: String): EngineProxy {
        val parsed = descriptorJson.parseToJsonElement(descriptor) as JsonObject
        return EngineProxy(tag, descriptor, handler.placement, handler.requirements(parsed))
    }

    companion object {
        val allSchemes: List<String> get() = entries.flatMap { it.handler.schemes }

        fun fromWire(value: String): ProxyProtocol? = entries.firstOrNull { it.wire == value }
    }
}

interface ProtocolHandler {
    val schemes: List<String>

    val placement: EnginePlacement get() = EnginePlacement.OUTBOUND

    fun recognizes(input: String): Boolean = schemes.any { input.startsWith("$it://") }

    fun parse(input: String): ParsedLink

    fun server(descriptor: JsonObject): ServerAddress?

    fun requirements(descriptor: JsonObject): Set<EngineRequirement>
}

data class ParsedLink(val name: String, val outbound: JsonObject)

data class ServerAddress(val host: String, val port: Int)

data class ParsedProfile(
    val name: String,
    val protocol: ProxyProtocol,
    val outbound: JsonObject,
    val link: String
) {
    fun outboundJson(): String = descriptorJson.encodeToString(JsonObject.serializer(), outbound)
}

internal val descriptorJson = Json { ignoreUnknownKeys = true }
