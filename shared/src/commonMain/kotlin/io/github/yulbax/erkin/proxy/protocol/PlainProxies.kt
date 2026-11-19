package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Socks : LinkHandler {
    private const val DEFAULT_PORT = 1080

    override val coreType = "socks5"
    override val schemes: List<String> = listOf("socks5", "socks")

    override fun parse(link: ShareLink): ParsedLink {
        val credentials = Credentials.of(link.userInfo)

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: DEFAULT_PORT)
            credentials?.let { putCredentials(it.username, it.password) }
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

object Https : LinkHandler {
    private const val SCHEME = "https"
    private val ROOT_PATHS = setOf("", "/")

    override val coreType = "http"
    override val schemes: List<String> = listOf(SCHEME)

    override fun normalize(proxy: JsonObject): JsonObject? = if (proxy.flag("tls")) super.normalize(proxy) else null

    override fun recognizes(link: ShareLink): Boolean =
        super.recognizes(link) && link.port != null && link.path in ROOT_PATHS && link.query.isEmpty() &&
            (link.user != null || link.name != null)

    override fun parse(link: ShareLink): ParsedLink {
        val credentials = Credentials.of(link.userInfo)

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, requireNotNull(link.port) { "missing port" })
            put("udp", false)
            put("tls", true)
            put("sni", link.host)
            credentials?.let { putCredentials(it.username, it.password) }
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

private data class Credentials(val username: String, val password: String) {
    companion object {
        fun of(userInfo: String?): Credentials? {
            val decoded = userInfo?.takeIf { it.isNotEmpty() } ?: return null
            val plain = if (':' in decoded) decoded else decodeBase64Lenient(decoded)?.takeIf { ':' in it } ?: decoded
            return Credentials(plain.substringBefore(':'), plain.substringAfter(':', ""))
        }
    }
}

internal fun JsonObjectBuilder.putCredentials(username: String, password: String) {
    put("username", username)
    put("password", password)
}
