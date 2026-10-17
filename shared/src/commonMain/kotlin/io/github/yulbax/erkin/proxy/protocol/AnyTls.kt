package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object AnyTls : ProtocolHandler {
    override val schemes: List<String> = listOf("anytls")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val q = uri.queryMap()
        val password = decodeComponent(uri.rawUserInfo.orEmpty())
        require(password.isNotEmpty()) { "AnyTLS links need a password" }

        val descriptor = buildJsonObject {
            proxy("anytls", uri.host, if (uri.port > 0) uri.port else 443)
            put("password", password)
            tls(TlsStyle.SNI, q["sni"] ?: uri.host, q["fp"] ?: "", q.insecure(), q["alpn"] ?: "")
        }
        return ParsedLink(uri.fragmentName(uri.host), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(enabled = true)
}
