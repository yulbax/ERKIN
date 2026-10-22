package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object Hysteria2 : ProtocolHandler {
    override val schemes: List<String> = listOf("hysteria2", "hy2")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input.replaceFirst("hy2://", "hysteria2://"))
        val q = uri.queryMap()
        val sni = q["sni"] ?: uri.hostName

        val descriptor = buildJsonObject {
            proxy("hysteria2", uri.hostName, if (uri.port > 0) uri.port else 443)
            put("password", uri.userInfo ?: "")
            q["obfs"]?.takeIf { it.isNotEmpty() }?.let { obfs ->
                put("obfs", obfs)
                q["obfs-password"]?.takeIf { it.isNotEmpty() }?.let { put("obfs-password", it) }
            }
            tls(TlsStyle.SNI, sni, fp = "", insecure = q["insecure"] == "1", alpn = q["alpn"] ?: "")
        }
        return ParsedLink(uri.fragmentName(uri.hostName), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
