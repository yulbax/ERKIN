package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.proxy.ServerAddress
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI

object Hysteria2 : ProtocolHandler {
    override val schemes: List<String> = listOf("hysteria2", "hy2")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input.replaceFirst("hy2://", "hysteria2://"))
        val q = uri.queryMap()
        val sni = q["sni"] ?: uri.host

        val outbound = buildJsonObject {
            put("type", "hysteria2")
            put("server", uri.host)
            put("server_port", if (uri.port > 0) uri.port else 443)
            put("password", uri.userInfo ?: "")
            q["obfs"]?.takeIf { it.isNotEmpty() }?.let { obfs ->
                putJsonObject("obfs") {
                    put("type", obfs)
                    q["obfs-password"]?.let { put("password", it) }
                }
            }
            put("tls", tlsBlock(sni, null, null, null, "", q["insecure"] == "1", q["alpn"] ?: ""))
        }
        return ParsedLink(uri.fragmentName(uri.host), outbound)
    }

    override fun server(descriptor: JsonObject): ServerAddress? = outboundServer(descriptor)

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
