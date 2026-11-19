package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Trojan : LinkHandler {
    private const val DEFAULT_PORT = 443

    override val coreType = "trojan"
    override val schemes: List<String> = listOf("trojan")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val net = q["type"] ?: "tcp"
        val host = q["host"] ?: ""
        val sni = q["sni"] ?: host.ifEmpty { link.host }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: DEFAULT_PORT)
            put("password", link.userInfo.orEmpty())
            tls(TlsStyle.SNI, sni, q["fp"] ?: "", q["allowInsecure"] == "1", q["alpn"] ?: "")
            transport(net, q["path"] ?: "/", host, q["serviceName"], XhttpParams(q["mode"], q["extra"]))
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(enabled = true)
}
