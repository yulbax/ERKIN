package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object Ssh : LinkHandler {
    private const val DEFAULT_PORT = 22

    override val coreType = "ssh"
    override val schemes: List<String> = listOf("ssh")

    override fun parse(link: ShareLink): ParsedLink {
        val q = link.query
        val username = requireNotNull(link.user?.takeIf { it.isNotEmpty() }) { "SSH links need a user" }
        val hostKeys = q["hk"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val descriptor = buildJsonObject {
            proxy(coreType, link.host, link.port ?: DEFAULT_PORT)
            put("username", username)
            link.password?.takeIf { it.isNotEmpty() }?.let { put("password", it) }
            q["pk"]?.takeIf { it.isNotEmpty() }?.let { put("private-key", it) }
            q["pkp"]?.takeIf { it.isNotEmpty() }?.let { put("private-key-passphrase", it) }
            if (hostKeys.isNotEmpty()) putJsonArray("host-key") { hostKeys.forEach { add(it) } }
        }
        return ParsedLink(link.title, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
