package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object OpenVpn : ProtocolHandler {
    private const val DEFAULT_PORT = 1194
    private val WHITESPACE = Regex("\\s+")
    private val BLOCK = Regex("<([a-z0-9-]+)>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val REMOTE = Regex("^\\s*remote\\s+\\S+", RegexOption.MULTILINE)
    private val CLIENT = Regex("^\\s*(client|tls-client)\\s*$", RegexOption.MULTILINE)
    private val INLINE = listOf("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2")
    private val PASSTHROUGH = listOf("dev", "cipher", "auth", "data-ciphers-fallback", "key-direction")

    override val schemes: List<String> = emptyList()

    override fun recognizes(input: String): Boolean =
        "://" !in input.lineSequence().first() && REMOTE.containsMatchIn(input) && (CLIENT.containsMatchIn(input) || "<ca>" in input)

    override fun parse(input: String): ParsedLink {
        val blocks = BLOCK.findAll(input).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val directives = BLOCK.replace(input, "").lineSequence()
            .map { it.substringBefore('#').substringBefore(';').trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(WHITESPACE) }
            .toList()
        fun first(name: String): List<String>? = directives.firstOrNull { it[0] == name }

        val remote = requireNotNull(first("remote")) { "OpenVPN config has no remote" }
        val host = remote.getOrNull(1) ?: error("OpenVPN remote has no host")
        val port = remote.getOrNull(2)?.toIntOrNull() ?: first("port")?.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
        INLINE.forEach { name ->
            require(first(name) == null || name in blocks) { "OpenVPN $name must be inline" }
        }
        val credentials = blocks["auth-user-pass"]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
        require(first("auth-user-pass") == null || credentials?.size == 2) { "OpenVPN login and password must be inline" }

        val descriptor = buildJsonObject {
            proxy("openvpn", host, port)
            (remote.getOrNull(3) ?: first("proto")?.getOrNull(1))?.let { put("proto", it) }
            PASSTHROUGH.forEach { name -> first(name)?.getOrNull(1)?.let { put(name, it) } }
            first("data-ciphers")?.getOrNull(1)?.let { ciphers ->
                putJsonArray("data-ciphers") { ciphers.split(':').filter { it.isNotEmpty() }.forEach { add(it) } }
            }
            first("comp-lzo")?.let { put("comp-lzo", it.getOrNull(1) ?: "yes") }
            first("tun-mtu")?.getOrNull(1)?.toIntOrNull()?.let { put("mtu", it) }
            INLINE.forEach { name -> blocks[name]?.let { put(name, it) } }
            credentials?.let {
                put("username", it[0])
                put("password", it[1])
            }
        }
        return ParsedLink(host, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
