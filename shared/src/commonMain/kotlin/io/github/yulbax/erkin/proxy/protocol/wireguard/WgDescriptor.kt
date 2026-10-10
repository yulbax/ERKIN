package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ServerAddress
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal enum class WgDialect(val endpointType: String, val presharedKeyField: String, val supportsReserved: Boolean) {
    AMNEZIA("awg", "preshared_key", supportsReserved = false),
    UPSTREAM("wireguard", "pre_shared_key", supportsReserved = true)
}

internal fun wgEndpoint(
    dialect: WgDialect,
    config: WgConfig,
    extras: JsonObjectBuilder.() -> Unit = {}
): JsonObject = buildJsonObject {
    put("type", dialect.endpointType)
    putJsonArray("address") { config.addresses.forEach { add(it) } }
    put("private_key", config.privateKey)
    config.mtu?.let { put("mtu", it) }
    config.listenPort?.let { put("listen_port", it) }
    putJsonArray("peers") { config.peers.forEach { add(wgPeer(dialect, it)) } }
    extras()
}

private fun wgPeer(dialect: WgDialect, peer: WgPeer): JsonObject = buildJsonObject {
    put("address", peer.host)
    put("port", peer.port)
    put("public_key", peer.publicKey)
    peer.presharedKey?.let { put(dialect.presharedKeyField, it) }
    putJsonArray("allowed_ips") { peer.allowedIps.forEach { add(it) } }
    peer.keepalive?.let { put("persistent_keepalive_interval", it) }
    if (dialect.supportsReserved) {
        peer.reserved?.takeIf { bytes -> bytes.any { it != 0 } }?.let { bytes ->
            putJsonArray("reserved") { bytes.forEach { add(it) } }
        }
    }
}

internal fun peerServer(descriptor: JsonObject): ServerAddress? {
    val peer = (descriptor["peers"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val host = runCatching { peer["address"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
    val port = runCatching { peer["port"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: return null
    return ServerAddress(host, port)
}

internal fun JsonObject.hasIpv6Address(): Boolean =
    (this["address"] as? JsonArray)?.any { (it as? JsonPrimitive)?.content?.contains(':') == true } == true

internal fun withPrefix(address: String): String = when {
    address.contains('/') -> address
    address.contains(':') -> "$address/128"
    else -> "$address/32"
}
