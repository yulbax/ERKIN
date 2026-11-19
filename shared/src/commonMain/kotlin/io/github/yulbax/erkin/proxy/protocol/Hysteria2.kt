package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Hysteria2 : LinkHandler {
    override val coreType = "hysteria2"
    override val schemes: List<String> = listOf("hysteria2", "hy2")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val sni = q["sni"] ?: link.host

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: 443)
            put("password", link.userInfo.orEmpty())
            q["obfs"]?.takeIf { it.isNotEmpty() }?.let { obfs ->
                put("obfs", obfs)
                q["obfs-password"]?.takeIf { it.isNotEmpty() }?.let { put("obfs-password", it) }
            }
            tls(TlsStyle.SNI, sni, fp = "", insecure = q["insecure"] == "1", alpn = q["alpn"] ?: "")
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
