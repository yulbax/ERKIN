package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.proxy.ServerAddress
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object Vless : ProtocolHandler {
    override val schemes: List<String> = listOf("vless")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val q = uri.queryMap()
        val security = q["security"] ?: "none"
        val net = q["type"] ?: "tcp"
        val host = q["host"] ?: ""
        val path = q["path"]?.let { decodeComponent(it) } ?: "/"
        val sni = q["sni"] ?: host.ifEmpty { uri.host }

        val outbound = buildJsonObject {
            put("type", "vless")
            put("server", uri.host)
            put("server_port", uri.port)
            put("uuid", uri.userInfo ?: "")
            q["flow"]?.takeIf { it.isNotEmpty() }?.let { put("flow", it) }
            transport(net, path, host, q["serviceName"])?.let { put("transport", it) }
            when (security) {
                "tls" -> put("tls", tlsBlock(sni, null, null, null, q["fp"] ?: "", q["allowInsecure"] == "1", q["alpn"] ?: ""))
                "reality" -> put("tls", tlsBlock(sni, true, q["pbk"], q["sid"], q["fp"] ?: "chrome", false, q["alpn"] ?: ""))
            }
        }
        return ParsedLink(uri.fragmentName(uri.host), outbound)
    }

    override fun server(descriptor: JsonObject): ServerAddress? = outboundServer(descriptor)

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(descriptor)
}
