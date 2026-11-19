package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkHandler
import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ShareLink
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Shadowsocks : LinkHandler {
    private const val DEFAULT_PORT = 8388

    override val coreType = "ss"
    override val schemes: List<String> = listOf("ss")

    override fun parse(link: ShareLink): ParsedLink {
        val method: String
        val password: String
        val host: String
        val port: Int
        val userInfo = link.userInfo
        if (userInfo != null) {
            val creds = if (userInfo.contains(':')) userInfo else decodeBase64Lenient(userInfo) ?: error("invalid ss credentials")
            method = creds.substringBefore(':')
            password = creds.substringAfter(':')
            host = link.host
            port = link.port ?: DEFAULT_PORT
        } else {
            val decoded = decodeBase64Lenient(link.body) ?: error("invalid ss payload")
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':').substringBeforeLast('@')
            val server = decoded.substringAfterLast('@')
            host = server.substringBeforeLast(':')
            port = server.substringAfterLast(':').toIntOrNull() ?: DEFAULT_PORT
        }

        val descriptor = buildJsonObject {
            proxy(coreType, host, port)
            put("cipher", method)
            put("password", password)
        }
        return ParsedLink(link.name ?: host, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
