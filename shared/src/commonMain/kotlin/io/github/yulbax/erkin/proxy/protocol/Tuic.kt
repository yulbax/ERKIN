package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Tuic : LinkHandler {
    override val coreType = "tuic"
    override val schemes: List<String> = listOf("tuic")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val uuid = link.user.orEmpty()
        val password = link.password.orEmpty()
        require(uuid.isNotEmpty() && password.isNotEmpty()) { "TUIC links need a UUID and a password" }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: 443)
            put("uuid", uuid)
            put("password", password)
            q["congestion_control"]?.takeIf { it.isNotEmpty() }?.let { put("congestion-controller", it) }
            q["udp_relay_mode"]?.takeIf { it.isNotEmpty() }?.let { put("udp-relay-mode", it) }
            if (q.enabled("reduce_rtt")) put("reduce-rtt", true)
            if (q.enabled("disable_sni")) put("disable-sni", true)
            tls(TlsStyle.SNI, q["sni"] ?: link.host, fp = "", insecure = q.insecure(), alpn = q["alpn"] ?: "")
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
