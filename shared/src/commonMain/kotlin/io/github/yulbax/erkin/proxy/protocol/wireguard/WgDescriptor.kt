package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.protocol.proxy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun wgProxy(config: WgConfig, amneziaOptions: JsonObject? = null): JsonObject = buildJsonObject {
    val peer = config.peers.first()
    proxy("wireguard", peer.host, peer.port)
    config.addresses.firstOrNull { !it.contains(':') }?.let { put("ip", it) }
    config.addresses.firstOrNull { it.contains(':') }?.let { put("ipv6", it) }
    put("private-key", config.privateKey)
    put("public-key", peer.publicKey)
    peer.presharedKey?.let { put("pre-shared-key", it) }
    peer.allowedIps.takeIf { it.isNotEmpty() }?.let { ips -> putJsonArray("allowed-ips") { ips.forEach { add(it) } } }
    peer.keepalive?.let { put("persistent-keepalive", it) }
    peer.reserved?.takeIf { bytes -> bytes.any { it != 0 } }?.let { bytes ->
        putJsonArray("reserved") { bytes.forEach { add(it) } }
    }
    config.mtu?.let { put("mtu", it) }
    amneziaOptions?.let { put("amnezia-wg-option", it) }
}

internal fun JsonObject.hasIpv6Address(): Boolean = containsKey("ipv6")

internal fun withPrefix(address: String): String = when {
    address.contains('/') -> address
    address.contains(':') -> "$address/128"
    else -> "$address/32"
}
