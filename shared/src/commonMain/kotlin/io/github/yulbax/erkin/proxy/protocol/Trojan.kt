package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.proxy.ServerAddress
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object Trojan : ProtocolHandler {
    override val schemes: List<String> = listOf("trojan")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val q = uri.queryMap()
        val net = q["type"] ?: "tcp"
        val host = q["host"] ?: ""
        val sni = q["sni"] ?: host.ifEmpty { uri.host }

        val outbound = buildJsonObject {
            put("type", "trojan")
            put("server", uri.host)
            put("server_port", uri.port)
            put("password", uri.userInfo ?: "")
            transport(net, q["path"]?.let { decodeComponent(it) } ?: "/", host, q["serviceName"])?.let { put("transport", it) }
            put("tls", tlsBlock(sni, null, null, null, q["fp"] ?: "", q["allowInsecure"] == "1", q["alpn"] ?: ""))
        }
        return ParsedLink(uri.fragmentName(uri.host), outbound)
    }

    override fun server(descriptor: JsonObject): ServerAddress? = outboundServer(descriptor)

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(descriptor)
}
