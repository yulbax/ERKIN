package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

class SocksHealthProbe(
    private val byeDpiProbeUrl: String
) : HealthProbe {

    override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
        val engine = params.engine
        val credentials = SocksCredentials(engine.probeUsername, engine.probePassword)
        var country = ""
        while (true) {
            val vpnDelay = if (params.vpnActive) engine.measureVpnDelay() else null
            val byedpiUp = params.byeDpiPort?.let { byeDpiReachable(it) } == true
            if (vpnDelay != null && country.isEmpty()) {
                country = resolveCountry(engine.probeSocksPort, credentials) ?: country
            }
            val snapshot = HealthSnapshot(
                vpnActive = params.vpnActive,
                vpnDelayMs = vpnDelay,
                vpnCountry = country,
                byedpiActive = params.byeDpiPort != null,
                byedpiUp = byedpiUp
            )
            emit(snapshot)
            delay((if (snapshot.vpnHealthy) HEALTH_INTERVAL_MS else HEALTH_RETRY_MS).milliseconds)
        }
    }

    private val byeDpiCandidates = listOf(byeDpiProbeUrl) + ByeDpiSites.QUICK.sites.map { "https://$it" }
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
