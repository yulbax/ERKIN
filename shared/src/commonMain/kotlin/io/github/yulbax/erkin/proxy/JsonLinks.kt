@file:OptIn(ExperimentalEncodingApi::class)

package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal enum class LinkTransport(val wire: String) {
    TCP("tcp"),
    WS("ws"),
    GRPC("grpc"),
    HTTP("http"),
    HTTPUPGRADE("httpupgrade"),
    XHTTP("xhttp")
}

internal enum class LinkSecurity(val wire: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality");

    companion object {
        fun fromWire(value: String): LinkSecurity? = entries.firstOrNull { it.wire == value }
    }
}

internal data class TlsParams(
    val sni: String? = null,
    val insecure: Boolean = false,
    val alpn: List<String> = emptyList(),
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null
) {
    fun query(sniKey: String = "sni", insecureKey: String = "allowInsecure"): Map<String, String?> = mapOf(
        sniKey to sni,
        insecureKey to if (insecure) "1" else null,
        "alpn" to alpn.joinToString(","),
        "fp" to fingerprint,
        "pbk" to publicKey,
        "sid" to shortId
    )
}

internal data class StreamParams(
    val transport: LinkTransport = LinkTransport.TCP,
    val security: LinkSecurity = LinkSecurity.NONE,
    val tls: TlsParams = TlsParams(),
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
    val mode: String? = null,
    val extra: String? = null
) {
    fun query(): Map<String, String?> = buildMap {
        put("security", security.takeIf { it != LinkSecurity.NONE }?.wire)
        put("type", transport.takeIf { it != LinkTransport.TCP }?.wire)
        if (security != LinkSecurity.NONE) putAll(tls.query())
        put("host", host)
        put("path", path)
        put("serviceName", serviceName)
        put("mode", mode)
        put("extra", extra)
    }
}

internal fun parseJson(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

private fun JsonElement?.scalar(): String? = when (this) {
    is JsonArray -> firstOrNull().scalar()
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotEmpty() }
    else -> null
}

internal fun JsonObject.text(key: String): String? = this[key].scalar()

internal fun JsonObject.hasString(key: String): Boolean = (this[key] as? JsonPrimitive)?.isString == true

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

internal fun JsonObject.texts(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { it.scalar() }
    else -> listOfNotNull(value.scalar())
}

internal fun shareLinkText(
    scheme: String,
    host: String,
    port: Int,
    user: String? = null,
    password: String? = null,
    query: Map<String, String?> = emptyMap(),
    name: String? = null
): String {
    val account = user?.takeIf { it.isNotEmpty() }
    return ShareLink(
        scheme = scheme,
        body = "",
        user = account,
        password = password?.takeIf { account != null && it.isNotEmpty() },
        host = host,
        portSpec = port.toString(),
        path = "",
        query = query.mapNotNull { (key, value) -> value?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap(),
        name = name?.takeIf { it.isNotEmpty() } ?: host
    ).format()
}

internal fun vmessLink(name: String?, host: String, port: Int, id: String, alterId: Int?, cipher: String?, stream: StreamParams): String {
    val body = buildJsonObject {
        put("v", "2")
        put("ps", name ?: host)
        put("add", host)
        put("port", port.toString())
        put("id", id)
        put("aid", (alterId ?: 0).toString())
        put("scy", cipher ?: "auto")
        put("net", stream.transport.wire)
        put("host", stream.host.orEmpty())
        put("path", (if (stream.transport == LinkTransport.GRPC) stream.serviceName else stream.path).orEmpty())
        put("tls", if (stream.security == LinkSecurity.NONE) "" else LinkSecurity.TLS.wire)
        put("sni", stream.tls.sni.orEmpty())
        put("alpn", stream.tls.alpn.joinToString(","))
    }
    return ShareLink("vmess", Base64.encode(body.toString().encodeToByteArray()), null, null, "", null, "", emptyMap(), null).format()
}

internal data class UdpOverTcp(val version: Int?)

internal fun shadowsocksLink(
    host: String,
    port: Int,
    method: String,
    password: String,
    plugin: String?,
    pluginOpts: String?,
    name: String?,
    udpOverTcp: UdpOverTcp? = null
): String {
    val credentials = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("$method:$password".encodeToByteArray())
    val pluginSpec = listOfNotNull(plugin, pluginOpts).filter { it.isNotEmpty() }.joinToString(";")
    val query = mapOf(
        "plugin" to pluginSpec,
        "uot" to udpOverTcp?.let { "1" },
        "udp-over-tcp-version" to udpOverTcp?.version?.toString()
    )
    return shareLinkText("ss", host, port, user = credentials, query = query, name = name)
}

internal fun wireguardLink(
    privateKey: String,
    host: String,
    port: Int,
    publicKey: String,
    addresses: List<String>,
    presharedKey: String?,
    reserved: List<String>,
    mtu: Int?,
    name: String?
): String = shareLinkText(
    "wireguard", host, port, user = privateKey,
    query = mapOf(
        "publickey" to publicKey,
        "address" to addresses.joinToString(","),
        "presharedkey" to presharedKey,
        "reserved" to reserved.joinToString(","),
        "mtu" to mtu?.toString()
    ),
    name = name
)
