package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class SocksHealthProbe(
    private val byeDpiProbeUrl: String
) : HealthProbe {

    override fun observe(params: ProbeParams): Flow<HealthSnapshot> = channelFlow {
        val engine = params.engine
        val credentials = SocksCredentials(engine.probeUsername, engine.probePassword)
        val access = Mutex()
        var latest = HealthSnapshot(
            vpnActive = params.vpnActive,
            vpnDelayMs = null,
            vpnCountry = "",
            byedpiActive = params.byeDpiPort != null,
            byedpiUp = false,
            measured = HealthChannel.VPN
        )
        suspend fun publish(update: (HealthSnapshot) -> HealthSnapshot) = access.withLock {
            latest = update(latest)
            send(latest)
        }
        if (params.vpnActive) launch {
            var country = ""
            while (true) {
                val vpnDelay = engine.measureVpnDelay()
                if (vpnDelay != null && country.isEmpty()) {
                    country = resolveCountry(engine.probeSocksPort, credentials) ?: country
                }
                publish { it.copy(vpnDelayMs = vpnDelay, vpnCountry = country, measured = HealthChannel.VPN) }
                delay((if (vpnDelay != null) HEALTH_INTERVAL_MS else HEALTH_RETRY_MS).milliseconds)
            }
        }
        params.byeDpiPort?.let { port ->
            launch {
                while (true) {
                    val up = byeDpiReachable(port)
                    publish { it.copy(byedpiUp = up, measured = HealthChannel.BYEDPI) }
                    delay(HEALTH_INTERVAL_MS.milliseconds)
                }
            }
        }
    }

    private val byeDpiCandidates = listOf(byeDpiProbeUrl) + ByeDpiSites.HEALTH_CHECK.map { "https://$it" }
    private var lastGoodByeDpi = 0

    private suspend fun byeDpiReachable(socksPort: Int): Boolean {
        repeat(BYEDPI_ATTEMPTS) { attempt ->
            val index = (lastGoodByeDpi + attempt) % byeDpiCandidates.size
            val url = byeDpiCandidates[index]
            if (SocksHttp.request(socksPort, url, "HEAD", BYEDPI_TIMEOUT_MS, null) { it.responseCode } != null) {
                lastGoodByeDpi = index
                return true
            }
        }
        lastGoodByeDpi = (lastGoodByeDpi + BYEDPI_ATTEMPTS) % byeDpiCandidates.size
        return false
    }

    private suspend fun resolveCountry(socksPort: Int, credentials: SocksCredentials): String? =
        SocksHttp.request(socksPort, GEO_URL, "GET", PROBE_TIMEOUT_MS, credentials) { connection ->
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            COUNTRY_REGEX.find(body)?.groupValues?.get(1)?.uppercase()?.takeIf { it != UNKNOWN_COUNTRY }
        }

    private companion object {
        const val HEALTH_INTERVAL_MS = 15_000L
        const val HEALTH_RETRY_MS = 3_000L
        const val PROBE_TIMEOUT_MS = 6_000
        const val BYEDPI_TIMEOUT_MS = 4_000
        const val BYEDPI_ATTEMPTS = 3
        const val GEO_URL = "https://www.cloudflare.com/cdn-cgi/trace"
        const val UNKNOWN_COUNTRY = "XX"
        val COUNTRY_REGEX = Regex("^loc=([A-Za-z]{2})$", RegexOption.MULTILINE)
    }
}
