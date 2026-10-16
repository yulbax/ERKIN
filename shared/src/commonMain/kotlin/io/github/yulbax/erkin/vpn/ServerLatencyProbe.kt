package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface ProxyLatencyTester {
    suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeoutMs: Int): String
}

class ServerLatencyProbe(
    private val store: VpnSessionStore,
    private val tester: ProxyLatencyTester
) {
    suspend fun measureAll(): Map<Long, ProxyDelay> {
        val inputs = store.load()
        if (inputs.profiles.none { it.isLink }) return emptyMap()
        val proxies = with(EngineConfigComposer) { inputs.profiles.filter { it.isLink }.map { it.engineProxy() } }
        return parse(tester.measure(proxies, inputs.settings.networkOptions(), ProbeUrls.VPN, TIMEOUT_MS))
    }

    companion object {
        const val TIMEOUT_MS = 5_000

        fun parse(results: String): Map<Long, ProxyDelay> =
            Json.parseToJsonElement(results).jsonObject.mapNotNull { (tag, value) ->
                val id = ProxyTag.profileId(tag) ?: return@mapNotNull null
                val ms = value.jsonPrimitive.intOrNull ?: return@mapNotNull null
                id to if (ms > 0) ProxyDelay.Measured(ms) else ProxyDelay.Failed
            }.toMap()
    }
}
