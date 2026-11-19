package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Vless : LinkHandler {
    private const val DEFAULT_PORT = 443

    override val coreType = "vless"
    override val schemes: List<String> = listOf("vless")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val security = q["security"] ?: "none"
        val net = q["type"] ?: "tcp"
        val host = q["host"] ?: ""
        val path = q["path"] ?: "/"
        val sni = q["sni"] ?: host.ifEmpty { link.host }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: DEFAULT_PORT)
            put("uuid", link.userInfo.orEmpty())
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
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(descriptor.flag("tls"))
}
