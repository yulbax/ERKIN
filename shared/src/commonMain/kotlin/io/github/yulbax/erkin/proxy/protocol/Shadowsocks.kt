package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object Shadowsocks : LinkHandler {
    private const val DEFAULT_PORT = 8388
    private const val UDP_OVER_TCP = "udp-over-tcp"
    private const val UDP_OVER_TCP_VERSION = "udp-over-tcp-version"

    override val coreType = "ss"
    override val schemes: List<String> = listOf("ss")

    override fun parse(link: ShareLink): ParsedLink {
        val method: String
        val password: String
        val host: String
        val port: Int
        val userInfo = link.userInfo
        if (userInfo != null) {
            val creds = if (userInfo.contains(':')) userInfo else decodeBase64Lenient(userInfo) ?: error("invalid ss credentials")
            method = creds.substringBefore(':')
            password = creds.substringAfter(':')
            host = link.host
            port = link.port ?: DEFAULT_PORT
        } else {
            val decoded = decodeBase64Lenient(link.body) ?: error("invalid ss payload")
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':').substringBeforeLast('@')
            val server = decoded.substringAfterLast('@')
            host = server.substringBeforeLast(':')
            port = server.substringAfterLast(':').toIntOrNull() ?: DEFAULT_PORT
        }

        val descriptor = buildJsonObject {
            proxy(coreType, host, port)
            put("cipher", method)
            put("password", password)
            link.query["plugin"]?.takeIf { it.isNotEmpty() }?.let { putPlugin(it) }
            if (link.query.enabled("uot") || link.query.enabled(UDP_OVER_TCP)) {
                put(UDP_OVER_TCP, true)
                link.query[UDP_OVER_TCP_VERSION]?.toIntOrNull()?.let { put(UDP_OVER_TCP_VERSION, it) }
            }
        }
        return ParsedLink(link.name ?: host, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

private enum class SsPlugin(val core: String, val names: List<String>) {
    OBFS("obfs", listOf("obfs-local", "simple-obfs")) {
        override fun JsonObjectBuilder.options(values: Map<String, String>) {
            put("mode", requireNotNull(values["obfs"]) { "obfs plugin needs a mode" })
            values["obfs-host"]?.takeIf { it.isNotEmpty() }?.let { put("host", it) }
        }
    },
    V2RAY("v2ray-plugin", listOf("v2ray-plugin")) {
        override fun JsonObjectBuilder.options(values: Map<String, String>) {
            put("mode", values["mode"]?.takeIf { it.isNotEmpty() } ?: "websocket")
            values["host"]?.takeIf { it.isNotEmpty() }?.let { put("host", it) }
            values["path"]?.takeIf { it.isNotEmpty() }?.let { put("path", it) }
            if ("tls" in values) put("tls", true)
        }
    };

    abstract fun JsonObjectBuilder.options(values: Map<String, String>)

    companion object {
        fun fromWire(name: String): SsPlugin? = entries.firstOrNull { name in it.names }
    }
}

private fun JsonObjectBuilder.putPlugin(spec: String) {
    val parts = spec.split(';')
    val plugin = requireNotNull(SsPlugin.fromWire(parts.first())) { "unsupported plugin ${parts.first()}" }
    val values = parts.drop(1).filter { it.isNotEmpty() }.associate { it.substringBefore('=') to it.substringAfter('=', "") }
    put("plugin", plugin.core)
    putJsonObject("plugin-opts") { with(plugin) { options(values) } }
}
