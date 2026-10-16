package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
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
        val path = q["path"] ?: "/"
        val sni = q["sni"] ?: host.ifEmpty { uri.host }

        val descriptor = buildJsonObject {
            proxy("vless", uri.host, uri.port)
            put("uuid", uri.userInfo ?: "")
            q["flow"]?.takeIf { it.isNotEmpty() }?.let { put("flow", it) }
            when (security) {
                "tls" -> tls(TlsStyle.V2RAY, sni, q["fp"] ?: "", q["allowInsecure"] == "1", q["alpn"] ?: "")
                "reality" -> tls(
                    TlsStyle.V2RAY, sni, q["fp"] ?: "chrome", insecure = false, alpn = q["alpn"] ?: "",
                    reality = Reality(q["pbk"] ?: "", q["sid"] ?: "")
                )
            }
            transport(net, path, host, q["serviceName"], XhttpParams(q["mode"], q["extra"]))
        }
        return ParsedLink(uri.fragmentName(uri.host), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(descriptor.flag("tls"))
}
