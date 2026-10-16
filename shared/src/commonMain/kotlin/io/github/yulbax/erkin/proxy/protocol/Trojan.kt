package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
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

        val descriptor = buildJsonObject {
            proxy("trojan", uri.host, uri.port)
            put("password", uri.userInfo ?: "")
            tls(TlsStyle.SNI, sni, q["fp"] ?: "", q["allowInsecure"] == "1", q["alpn"] ?: "")
            transport(net, q["path"] ?: "/", host, q["serviceName"], XhttpParams(q["mode"], q["extra"]))
        }
        return ParsedLink(uri.fragmentName(uri.host), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(enabled = true)
}
