package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.decodeBase64Lenient
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object CoreProxySource {
    const val SCHEME = "mihomo"

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(name: String, proxy: JsonObject): String {
        val payload = descriptorJson.encodeToString(JsonObject.serializer(), JsonObject(proxy - "name"))
        val body = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
        return ShareLink(
            scheme = SCHEME,
            body = body,
            user = null,
            password = null,
            host = "",
            portSpec = null,
            path = "",
            query = emptyMap(),
            name = name.takeIf { it.isNotEmpty() }
        ).format()
    }

    fun decode(link: ShareLink): JsonObject? =
        decodeBase64Lenient(link.body)?.let { runCatching { descriptorJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
}
