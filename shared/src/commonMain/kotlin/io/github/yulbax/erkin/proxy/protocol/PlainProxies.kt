package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI

object Socks : ProtocolHandler {
    private const val DEFAULT_PORT = 1080

    override val schemes: List<String> = listOf("socks5", "socks")

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val credentials = Credentials.of(uri.rawUserInfo)

        val descriptor = buildJsonObject {
            proxy("socks5", uri.hostName, if (uri.port > 0) uri.port else DEFAULT_PORT)
            credentials?.let {
                put("username", it.username)
                put("password", it.password)
            }
        }
        return ParsedLink(uri.fragmentName(uri.hostName), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

object Https : ProtocolHandler {
    private const val SCHEME = "https"
    private val ROOT_PATHS = setOf("", "/")

    override val schemes: List<String> = listOf(SCHEME)

    override fun recognizes(input: String): Boolean {
        if (!input.startsWith("$SCHEME://")) return false
        val uri = runCatching { URI(input) }.getOrNull() ?: return false
        return uri.port > 0 && uri.rawPath.orEmpty() in ROOT_PATHS && uri.rawQuery == null &&
            (uri.rawUserInfo != null || uri.rawFragment != null)
    }

    override fun parse(input: String): ParsedLink {
        val uri = URI(input)
        val credentials = Credentials.of(uri.rawUserInfo)

        val descriptor = buildJsonObject {
            proxy("http", uri.hostName, uri.port)
            put("udp", false)
            put("tls", true)
            put("sni", uri.hostName)
            credentials?.let {
                put("username", it.username)
                put("password", it.password)
            }
        }
        return ParsedLink(uri.fragmentName(uri.hostName), descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}

private data class Credentials(val username: String, val password: String) {
    companion object {
        fun of(rawUserInfo: String?): Credentials? {
            val decoded = rawUserInfo?.takeIf { it.isNotEmpty() }?.let(::decodeComponent) ?: return null
            val plain = if (':' in decoded) decoded else decodeBase64Lenient(decoded)?.takeIf { ':' in it } ?: decoded
            return Credentials(plain.substringBefore(':'), plain.substringAfter(':', ""))
        }
    }
}
