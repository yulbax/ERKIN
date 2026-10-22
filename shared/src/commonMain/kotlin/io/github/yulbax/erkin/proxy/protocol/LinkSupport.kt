package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.URLDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal data class XhttpParams(val mode: String?, val extra: String?)

private val XHTTP_EXTRA_FIELDS = mapOf(
    "headers" to "headers",
    "xPaddingBytes" to "x-padding-bytes",
    "noGRPCHeader" to "no-grpc-header",
    "scMaxEachPostBytes" to "sc-max-each-post-bytes",
    "scMinPostsIntervalMs" to "sc-min-posts-interval-ms",
    "uplinkHTTPMethod" to "uplink-http-method"
)

private val XHTTP_XMUX_FIELDS = mapOf(
    "maxConcurrency" to "max-concurrency",
    "maxConnections" to "max-connections",
    "cMaxReuseTimes" to "c-max-reuse-times",
    "hMaxRequestTimes" to "h-max-request-times",
    "hMaxReusableSecs" to "h-max-reusable-secs",
    "hKeepAlivePeriod" to "h-keep-alive-period"
)

internal enum class TlsStyle(val sniKey: String, val flagged: Boolean) {
    V2RAY("servername", flagged = true),
    SNI("sni", flagged = false)
}

internal data class Reality(val publicKey: String, val shortId: String)

internal val URI.hostName: String get() = host.orEmpty().removeSurrounding("[", "]")

internal fun JsonObjectBuilder.proxy(type: String, host: String, port: Int) {
    put("type", type)
    put("server", host.removeSurrounding("[", "]"))
    put("port", port)
    put("udp", true)
}

internal fun JsonObjectBuilder.transport(
    net: String,
    path: String,
    host: String,
    serviceName: String?,
    xhttp: XhttpParams = XhttpParams(null, null)
) {
    when (net) {
        "ws" -> {
            put("network", "ws")
            putJsonObject("ws-opts") {
                put("path", path)
                if (host.isNotEmpty()) putJsonObject("headers") { put("Host", host) }
            }
        }
        "grpc" -> {
            put("network", "grpc")
            putJsonObject("grpc-opts") { put("grpc-service-name", serviceName ?: path.trim('/')) }
        }
        "httpupgrade" -> {
            put("network", "ws")
            putJsonObject("ws-opts") {
                put("path", path)
                if (host.isNotEmpty()) putJsonObject("headers") { put("Host", host) }
                put("v2ray-http-upgrade", true)
            }
        }
        "http" -> {
            put("network", "h2")
            putJsonObject("h2-opts") {
                if (host.isNotEmpty()) putJsonArray("host") { add(host) }
                put("path", path)
            }
        }
        "xhttp", "splithttp" -> {
            put("network", "xhttp")
            putJsonObject("xhttp-opts") {
                put("path", path)
                if (host.isNotEmpty()) put("host", host)
                xhttp.mode?.takeIf { it.isNotEmpty() }?.let { put("mode", it) }
                val extra = xhttp.extra?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                extra?.let { fields ->
                    XHTTP_EXTRA_FIELDS.forEach { (from, to) -> fields[from]?.let { put(to, it) } }
                    (fields["xmux"] as? JsonObject)?.let { xmux ->
                        putJsonObject("reuse-settings") { XHTTP_XMUX_FIELDS.forEach { (from, to) -> xmux[from]?.let { put(to, it) } } }
                    }
                }
            }
        }
    }
}

internal fun JsonObjectBuilder.tls(
    style: TlsStyle,
    serverName: String,
    fp: String,
    insecure: Boolean,
    alpn: String = "",
    reality: Reality? = null
) {
    if (style.flagged) put("tls", true)
    if (serverName.isNotEmpty()) put(style.sniKey, serverName)
    if (insecure) put("skip-cert-verify", true)
    alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let {
        putJsonArray("alpn") { it.forEach { proto -> add(proto) } }
    }
    if (fp.isNotEmpty()) put(CLIENT_FINGERPRINT, fp)
    if (reality != null) putJsonObject("reality-opts") {
        put("public-key", reality.publicKey)
        if (reality.shortId.isNotEmpty()) put("short-id", reality.shortId)
    }
}

internal const val CLIENT_FINGERPRINT = "client-fingerprint"

internal fun tlsRequirements(enabled: Boolean): Set<EngineRequirement> =
    if (enabled) setOf(EngineRequirement.TLS_FINGERPRINT) else emptySet()

internal fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun URI.queryMap(): Map<String, String> {
    val raw = rawQuery ?: return emptyMap()
    return raw.split('&').mapNotNull {
        val k = it.substringBefore('=')
        val v = it.substringAfter('=', "")
        if (k.isEmpty()) null else k to decodeComponent(v)
    }.toMap()
}

private val TRUE_VALUES = setOf("1", "true")
private val INSECURE_KEYS = listOf("insecure", "allowInsecure", "allow_insecure", "skip-cert-verify")

internal fun Map<String, String>.enabled(key: String): Boolean = this[key]?.lowercase() in TRUE_VALUES

internal fun Map<String, String>.insecure(): Boolean = INSECURE_KEYS.any(::enabled)

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
