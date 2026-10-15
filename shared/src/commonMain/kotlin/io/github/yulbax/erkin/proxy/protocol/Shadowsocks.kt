package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.ProtocolHandler
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Shadowsocks : ProtocolHandler {
    override val schemes: List<String> = listOf("ss")

    override fun parse(input: String): ParsedLink {
        val body = input.removePrefix("ss://")
        val name = body.substringAfter('#', "").let { if (it.isEmpty()) "" else decodeComponent(it) }
        val main = body.substringBefore('#')

        val method: String
        val password: String
        val host: String
        val port: Int
        if (main.contains('@')) {
            val userInfo = main.substringBefore('@')
            val server = main.substringAfter('@')
            val creds = if (userInfo.contains(':')) userInfo else decodeBase64Lenient(userInfo) ?: error("invalid ss credentials")
            method = creds.substringBefore(':')
            password = creds.substringAfter(':')
            host = server.substringBeforeLast(':').substringBefore('?')
            port = server.substringAfterLast(':').substringBefore('?').toIntOrNull() ?: 8388
        } else {
            val decoded = decodeBase64Lenient(main) ?: error("invalid ss payload")
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':').substringBeforeLast('@')
            val server = decoded.substringAfterLast('@')
            host = server.substringBeforeLast(':')
            port = server.substringAfterLast(':').toIntOrNull() ?: 8388
        }

        val descriptor = buildJsonObject {
            proxy("ss", host, port)
            put("cipher", method)
            put("password", password)
        }
        return ParsedLink(name.ifEmpty { host }, descriptor)
    }

    override fun requirements(descriptor: JsonObject): Set<EngineRequirement> = emptySet()
}
