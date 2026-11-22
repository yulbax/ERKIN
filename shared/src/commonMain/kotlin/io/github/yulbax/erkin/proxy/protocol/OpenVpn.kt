package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.DocumentHandler
import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object OpenVpn : LinkHandler, DocumentHandler {
    override val coreType = "openvpn"
    override val schemes: List<String> = listOf("ovpn")
    private const val DEFAULT_PORT = 1194
    private const val TUNNEL_DNS = "remote-dns-resolve"
    private val WHITESPACE = Regex("\\s+")
    private val COMMENT = Regex("^[ \\t]*[#;].*\\n?", RegexOption.MULTILINE)
    private val BLOCK = Regex("<([a-z0-9-]+)>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val REMOTE = Regex("^\\s*remote\\s+\\S+", RegexOption.MULTILINE)
    private val CLIENT = Regex("^\\s*(client|tls-client)\\s*$", RegexOption.MULTILINE)
    private val ASKS_LOGIN = Regex("^\\s*auth-user-pass\\s*$", RegexOption.MULTILINE)
    private const val LOGIN_BLOCK = "<auth-user-pass>"
    private val LOGIN = Regex("<auth-user-pass>(.*?)</auth-user-pass>\\s*", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val INLINE = listOf("ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2")
    private val PASSTHROUGH = listOf("dev", "cipher", "auth", "data-ciphers-fallback", "key-direction")

    override fun recognizes(text: String): Boolean =
        "://" !in text.lineSequence().first() && REMOTE.containsMatchIn(text) && (CLIENT.containsMatchIn(text) || "<ca>" in text)

    fun document(source: String): String =
        ShareLink.parse(source.trim())?.takeIf { it.scheme in schemes }?.let(::linkDocument) ?: source

    private fun linkDocument(link: ShareLink): String? {
        val text = decodeBase64Lenient(link.body.substringBefore('?')) ?: return null
        val user = link.query["user"]
        val pass = link.query["pass"]
        return if (user != null && pass != null) withLogin(text, user, pass) else text
    }

    fun asksForLogin(input: String): Boolean {
        val text = document(input)
        return recognizes(text) && ASKS_LOGIN.containsMatchIn(text) && LOGIN_BLOCK !in text.lowercase()
    }

    fun withLogin(input: String, username: String, password: String): String =
        LOGIN.replace(document(input), "").trimEnd() + "\n$LOGIN_BLOCK\n$username\n$password\n</auth-user-pass>\n"

    fun username(input: String): String? =
        LOGIN.find(document(input))?.groupValues?.get(1)?.lines()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

    override fun parse(link: ShareLink): ParsedLink {
        val parsed = parse(requireNotNull(linkDocument(link)) { "invalid ovpn payload" })
        return ParsedLink(link.name ?: parsed.name, parsed.proxy)
    }

    override fun parse(text: String): ParsedLink {
        val config = COMMENT.replace(text, "")
        val blocks = BLOCK.findAll(config).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val directives = BLOCK.replace(config, "").lineSequence()
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
            proxy(coreType, host, port)
            (remote.getOrNull(3) ?: first("proto")?.getOrNull(1))?.let { put("proto", it) }
            PASSTHROUGH.forEach { name -> first(name)?.getOrNull(1)?.let { put(name, it) } }
            first("data-ciphers")?.getOrNull(1)?.let { ciphers ->
                putJsonArray("data-ciphers") { ciphers.split(':').filter { it.isNotEmpty() }.forEach { add(it) } }
            }
            first("comp-lzo")?.let { put("comp-lzo", it.getOrNull(1) ?: "yes") }
            first("tun-mtu")?.getOrNull(1)?.toIntOrNull()?.let { put("mtu", it) }
            INLINE.forEach { name -> blocks[name]?.let { put(name, it) } }
            credentials?.let { putCredentials(it[0], it[1]) }
        }
        return ParsedLink(host, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()

    override fun engineDescriptor(descriptor: JsonObject): JsonObject =
        if (TUNNEL_DNS in descriptor) descriptor else JsonObject(descriptor + (TUNNEL_DNS to JsonPrimitive(true)))
}
