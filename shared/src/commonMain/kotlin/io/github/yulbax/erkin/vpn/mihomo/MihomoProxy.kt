package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.proxy.protocol.CLIENT_FINGERPRINT
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object MihomoProxy {
    private const val DIALER_PROXY = "dialer-proxy"

    fun of(proxy: EngineProxy, preferredFingerprint: TlsFingerprint?): JsonObject? {
        val descriptor = runCatching { Json.parseToJsonElement(proxy.descriptor) as? JsonObject }.getOrNull() ?: return null
        if (descriptor["type"] !is JsonPrimitive) return null
        val fingerprint = preferredFingerprint?.wire?.takeIf { EngineRequirement.TLS_FINGERPRINT in proxy.requirements }
        return JsonObject(buildMap {
            put("name", JsonPrimitive(proxy.tag))
            putAll(descriptor - "name")
            fingerprint?.let { put(CLIENT_FINGERPRINT, JsonPrimitive(it)) }
            proxy.dialerProxy?.let { put(DIALER_PROXY, JsonPrimitive(it)) }
        })
    }
}
