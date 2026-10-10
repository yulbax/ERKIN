package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.ParsedLink
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
    private val WIREGUARD_CONTAINERS = listOf("awg", "wireguard")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(link: String): ParsedLink {
        val payload = decode(link.removePrefix("$SCHEME://").trim())
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
        val name = root["description"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
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
        val raw = Base64.UrlSafe.decode(encoded.padEnd((encoded.length + 3) / 4 * 4, '='))
        require(raw.size > SIZE_PREFIX_BYTES) { "payload too short" }
        val declaredSize = ((raw[0].toInt() and 0xFF) shl 24) or ((raw[1].toInt() and 0xFF) shl 16) or
            ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        require(declaredSize in 1..MAX_PAYLOAD_BYTES) { "unexpected payload size $declaredSize" }
        return inflate(raw, declaredSize).decodeToString()
    }

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
