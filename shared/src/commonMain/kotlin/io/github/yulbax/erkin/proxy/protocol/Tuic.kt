package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object Tuic : ProtocolHandler {
    override val schemes: List<String> = listOf("tuic")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val q = uri.queryMap()
        val userInfo = uri.rawUserInfo.orEmpty()
        val uuid = decodeComponent(userInfo.substringBefore(':'))
        val password = decodeComponent(userInfo.substringAfter(':', ""))
        require(uuid.isNotEmpty() && password.isNotEmpty()) { "TUIC links need a UUID and a password" }

        val descriptor = buildJsonObject {
            proxy("tuic", uri.host, if (uri.port > 0) uri.port else 443)
            put("uuid", uuid)
            put("password", password)
            q["congestion_control"]?.takeIf { it.isNotEmpty() }?.let { put("congestion-controller", it) }
            q["udp_relay_mode"]?.takeIf { it.isNotEmpty() }?.let { put("udp-relay-mode", it) }
            if (q.enabled("reduce_rtt")) put("reduce-rtt", true)
            if (q.enabled("disable_sni")) put("disable-sni", true)
            tls(TlsStyle.SNI, q["sni"] ?: uri.host, fp = "", insecure = q.insecure(), alpn = q["alpn"] ?: "")
        }
        return ParsedLink(uri.fragmentName(uri.host), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
