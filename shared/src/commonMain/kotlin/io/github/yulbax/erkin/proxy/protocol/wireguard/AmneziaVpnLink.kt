package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
import io.github.yulbax.erkin.proxy.protocol.decodeComponent
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object AmneziaVpnLink {
    const val SCHEME = "vpn"
    private const val SIZE_PREFIX_BYTES = 4
    private const val MAX_PAYLOAD_BYTES = 1 shl 20
    private const val ZLIB_HEADER = 0x78
    private val WIREGUARD_CONTAINERS = listOf("awg", "wireguard")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(link: String): ParsedLink {
        val body = link.removePrefix("$SCHEME://").trim()
        val payload = decode(body.substringBefore('#'))
        val name = body.substringAfter('#', "").trim().takeIf { it.isNotEmpty() }?.let(::decodeComponent)
        return if (WgIni.looksLikeConfig(payload)) configProfile(payload, name) else jsonProfile(payload, name)
    }

    private fun configProfile(payload: String, fragmentName: String?): ParsedLink {
        val config = WgIni.parse(payload)
        return ParsedLink(fragmentName ?: config.host, AmneziaWg.descriptor(config))
    }

    private fun jsonProfile(payload: String, fragmentName: String?): ParsedLink {
        val root = json.parseToJsonElement(payload).jsonObject
        val container = wireguardContainer(root) ?: error("no AmneziaWG container in link")
        val lastConfig = json.parseToJsonElement(
            container["last_config"]?.jsonPrimitive?.content ?: error("container has no last_config")
        ).jsonObject
        val configText = lastConfig["config"]?.jsonPrimitive?.content ?: error("last_config has no config")

        val config = WgIni.parse(
            configText,
            fallbackMtu = lastConfig["mtu"]?.jsonPrimitive?.content?.toIntOrNull()
        )
        val name = fragmentName ?: root["description"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: root["hostName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: config.host
        return ParsedLink(name, AmneziaWg.descriptor(config))
    }

    private fun wireguardContainer(root: JsonObject): JsonObject? {
        val containers = (root["containers"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonObject }.orEmpty()
        val default = root["defaultContainer"]?.jsonPrimitive?.content
        val preferred = containers.firstOrNull { it["container"]?.jsonPrimitive?.content == default }
        return listOfNotNull(preferred).plus(containers)
            .firstNotNullOfOrNull { entry -> WIREGUARD_CONTAINERS.firstNotNullOfOrNull { entry[it] as? JsonObject } }
    }

    private fun decode(encoded: String): String {
        val normalized = encoded
            .replace('+', '-')
            .replace('/', '_')
            .filterNot { it.isWhitespace() }
            .let { it.padEnd((it.length + 3) / 4 * 4, '=') }
        val raw = Base64.UrlSafe.decode(normalized)
        require(raw.isNotEmpty()) { "payload too short" }
        return if (isCompressed(raw)) inflate(raw, declaredSize(raw)).decodeToString() else raw.decodeToString()
    }

    private fun isCompressed(raw: ByteArray): Boolean =
        raw.size > SIZE_PREFIX_BYTES && raw[SIZE_PREFIX_BYTES] == ZLIB_HEADER.toByte() &&
            declaredSize(raw) in 1..MAX_PAYLOAD_BYTES

    private fun declaredSize(raw: ByteArray): Int =
        ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
            ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)

    private fun inflate(raw: ByteArray, declaredSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(raw, SIZE_PREFIX_BYTES, raw.size - SIZE_PREFIX_BYTES)
        val output = ByteArrayOutputStream(declaredSize)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0 && inflater.needsInput()) break
                output.write(buffer, 0, read)
                require(output.size() <= MAX_PAYLOAD_BYTES) { "payload too large" }
            }
        } finally {
            inflater.end()
        }
        return output.toByteArray()
    }
}
