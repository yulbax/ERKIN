package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.proxy.descriptorJson
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object Vmess : LinkHandler {
    override val coreType = "vmess"
    override val schemes: List<String> = listOf("vmess")

    override fun parse(link: ShareLink): ParsedLink {
        val decoded = decodeBase64Lenient(link.body) ?: error("invalid vmess payload")
        val obj = descriptorJson.parseToJsonElement(decoded).let { it as JsonObject }
        fun str(key: String): String = obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: ""
        val name = str("ps").ifEmpty { str("add") }
        val net = str("net").ifEmpty { "tcp" }
        val tls = str("tls") == "tls"
        val host = str("host")
        val path = str("path").ifEmpty { "/" }
        val sni = str("sni").ifEmpty { host }.ifEmpty { str("add") }

        val descriptor = buildJsonObject {
            proxy(coreType, str("add"), str("port").toIntOrNull() ?: 443)
            put("uuid", str("id"))
            put("alterId", str("aid").toIntOrNull() ?: 0)
            put("cipher", str("scy").ifEmpty { "auto" })
            if (tls) tls(TlsStyle.V2RAY, sni, fp = "", insecure = false, alpn = str("alpn"))
            transport(net, path, host, str("path"))
        }
        return ParsedLink(name, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = tlsRequirements(descriptor.flag("tls"))
}
