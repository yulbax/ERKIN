package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Ssr : LinkHandler {
    private const val FIELDS = 6
    private const val TAIL_FIELDS = 5

    override val coreType = "ssr"
    override val schemes: List<String> = listOf("ssr")

    override fun parse(link: ShareLink): ParsedLink {
        val payload = decodeBase64Lenient(link.body) ?: error("invalid ssr payload")
        val fields = payload.substringBefore("/?").split(':')
        require(fields.size >= FIELDS) { "invalid ssr payload" }
        val host = fields.dropLast(TAIL_FIELDS).joinToString(":")
        val (port, protocol, method, obfs, password) = fields.takeLast(TAIL_FIELDS)
        val params = payload.substringAfter("/?", "").split('&')
            .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        fun param(key: String): String = params[key]?.let(::decodeBase64Lenient).orEmpty()

        val descriptor = buildJsonObject {
            proxy(coreType, host, requireNotNull(port.toIntOrNull()) { "invalid ssr port" })
            put("cipher", method)
            put("password", decodeBase64Lenient(password).orEmpty())
            put("obfs", obfs)
            put("protocol", protocol)
            param("obfsparam").takeIf { it.isNotEmpty() }?.let { put("obfs-param", it) }
            param("protoparam").takeIf { it.isNotEmpty() }?.let { put("protocol-param", it) }
        }
        return ParsedLink(link.name ?: param("remarks").ifEmpty { host }, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
