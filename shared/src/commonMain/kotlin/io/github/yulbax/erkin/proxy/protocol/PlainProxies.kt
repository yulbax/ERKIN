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

private val ROOT_PATHS = setOf("", "/")

private fun ShareLink.isProxyEndpoint(): Boolean =
    port != null && path in ROOT_PATHS && query.isEmpty() && (user != null || name != null)

private fun httpProxy(link: ShareLink, tls: Boolean): JsonObject {
    val credentials = Credentials.of(link.userInfo)
    return buildJsonObject {
        proxy("http", link.host, requireNotNull(link.port) { "missing port" })
        put("udp", false)
        if (tls) {
            put("tls", true)
            put("sni", link.host)
        }
        credentials?.let { putCredentials(it.username, it.password) }
    }
}

object Https : LinkHandler {
    override val coreType = "http"
    override val schemes: List<String> = listOf("https")

    override fun normalize(proxy: JsonObject): JsonObject? = if (proxy.flag("tls")) super.normalize(proxy) else null

    override fun recognizes(link: ShareLink): Boolean = super.recognizes(link) && link.isProxyEndpoint()

    override fun parse(link: ShareLink): ParsedLink = ParsedLink(link.title, httpProxy(link, tls = true))

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

object Http : LinkHandler {
    override val coreType = "http"
    override val schemes: List<String> = listOf("http")

    override fun normalize(proxy: JsonObject): JsonObject? = if (proxy.flag("tls")) null else super.normalize(proxy)

    override fun recognizes(link: ShareLink): Boolean = super.recognizes(link) && link.isProxyEndpoint()

    override fun parse(link: ShareLink): ParsedLink = ParsedLink(link.title, httpProxy(link, tls = false))

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
