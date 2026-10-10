package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ServerAddress
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.URLDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal fun transport(net: String, path: String, host: String, serviceName: String?): JsonObject? =
    when (net) {
        "ws" -> buildJsonObject {
            put("type", "ws")
            put("path", path)
            if (host.isNotEmpty()) putJsonObject("headers") { put("Host", host) }
        }
        "grpc" -> buildJsonObject {
            put("type", "grpc")
            put("service_name", serviceName ?: path.trim('/'))
        }
        "httpupgrade" -> buildJsonObject {
            put("type", "httpupgrade")
            put("path", path)
            if (host.isNotEmpty()) put("host", host)
        }
        "http" -> buildJsonObject {
            put("type", "http")
            putJsonArray("path") { add(path) }
            if (host.isNotEmpty()) putJsonArray("host") { add(host) }
        }
        else -> null
    }

internal fun tlsBlock(
    serverName: String,
    reality: Boolean?,
    publicKey: String?,
    shortId: String?,
    fp: String,
    insecure: Boolean,
    alpn: String = ""
): JsonObject = buildJsonObject {
    put("enabled", true)
    if (serverName.isNotEmpty()) put("server_name", serverName)
    if (insecure) put("insecure", true)
    alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let {
        putJsonArray("alpn") { it.forEach { proto -> add(proto) } }
    }
    if (fp.isNotEmpty()) putJsonObject("utls") {
        put("enabled", true)
        put("fingerprint", fp)
    }
    if (reality == true) putJsonObject("reality") {
        put("enabled", true)
        put("public_key", publicKey ?: "")
        put("short_id", shortId ?: "")
    }
}

internal fun outboundServer(descriptor: JsonObject): ServerAddress? {
    val host = runCatching { descriptor["server"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
    val port = runCatching { descriptor["server_port"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: return null
    return ServerAddress(host, port)
}

internal fun tlsRequirements(descriptor: JsonObject): Set<EngineRequirement> =
    if (descriptor["tls"] is JsonObject) setOf(EngineRequirement.TLS_FINGERPRINT) else emptySet()

internal fun URI.queryMap(): Map<String, String> {
    val raw = rawQuery ?: return emptyMap()
    return raw.split('&').mapNotNull {
        val k = it.substringBefore('=')
        val v = it.substringAfter('=', "")
        if (k.isEmpty()) null else k to decodeComponent(v)
    }.toMap()
}

internal fun URI.fragmentName(fallback: String): String =
    fragment?.takeIf { it.isNotEmpty() }?.let { decodeComponent(it) } ?: fallback

internal fun decodeComponent(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeBase64Lenient(input: String): String? {
    val normalized = input.trim()
        .replace("\n", "")
        .replace("\r", "")
        .replace('-', '+')
        .replace('_', '/')
        .trimEnd('=')
    val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
    return runCatching { String(Base64.decode(padded)) }.getOrNull()
}
