package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AnyTls : LinkHandler {
    override val coreType = "anytls"
    override val schemes: List<String> = listOf("anytls")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val password = link.userInfo.orEmpty()
        require(password.isNotEmpty()) { "AnyTLS links need a password" }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: 443)
            put("password", password)
            tls(TlsStyle.SNI, q["sni"] ?: link.host, q["fp"] ?: "", q.insecure(), q["alpn"] ?: "")
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(enabled = true)
}
