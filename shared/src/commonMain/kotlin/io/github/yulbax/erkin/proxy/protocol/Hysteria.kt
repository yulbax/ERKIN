package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Hysteria : LinkHandler {
    private const val DEFAULT_PORT = 443
    private const val DEFAULT_MBPS = "100"
    private const val OBFS_MODE = "xplus"

    override val coreType = "hysteria"
    override val schemes: List<String> = listOf("hysteria")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val obfs = q["obfsParam"]?.takeIf { it.isNotEmpty() } ?: q["obfs"]?.takeIf { it.isNotEmpty() && it != OBFS_MODE }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: DEFAULT_PORT)
            putHopPorts(link)
            q["protocol"]?.takeIf { it.isNotEmpty() }?.let { put("protocol", it) }
            q["auth"]?.takeIf { it.isNotEmpty() }?.let { put("auth-str", it) }
            put("up", (q["up"] ?: q["upmbps"])?.takeIf { it.isNotEmpty() } ?: DEFAULT_MBPS)
            put("down", (q["down"] ?: q["downmbps"])?.takeIf { it.isNotEmpty() } ?: DEFAULT_MBPS)
            obfs?.let { put("obfs", it) }
            tls(TlsStyle.SNI, q["peer"] ?: link.host, fp = "", insecure = q.insecure(), alpn = q["alpn"] ?: "")
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
