package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object Mieru : LinkHandler {
    private const val PORT_RANGE = "port-range"
    private const val DEFAULT_TRANSPORT = "TCP"

    override val coreType = "mieru"
    override val schemes: List<String> = listOf("mierus")

    override fun portOf(proxy: JsonObject): Int? =
        super.portOf(proxy) ?: (proxy[PORT_RANGE] as? JsonPrimitive)?.contentOrNull?.substringBefore('-')?.toIntOrNull()

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val portSpec = requireNotNull(q["port"]?.takeIf { it.isNotEmpty() }) { "Mieru links need a port" }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, requireNotNull(portSpec.substringBefore('-').toIntOrNull()) { "invalid port" })
            putCredentials(link.user.orEmpty(), link.password.orEmpty())
            put("transport", q["protocol"]?.uppercase()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TRANSPORT)
            q["multiplexing"]?.takeIf { it.isNotEmpty() }?.let { put("multiplexing", it) }
            q["handshake-mode"]?.takeIf { it.isNotEmpty() }?.let { put("handshake-mode", it) }
            q["traffic-pattern"]?.takeIf { it.isNotEmpty() }?.let { put("traffic-pattern", it) }
        }.let { if ('-' in portSpec) JsonObject(it - "port" + (PORT_RANGE to JsonPrimitive(portSpec))) else it }
        return ParsedLink(link.name ?: q["profile"]?.takeIf { it.isNotEmpty() } ?: link.host, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
