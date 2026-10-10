package io.github.yulbax.erkin.vpn

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

class SocksHealthProbe(
    private val vpnProbeUrl: String,
    private val byeDpiProbeUrl: String
) : HealthProbe {

    override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
        val engine = params.engine
        val credentials = SocksCredentials(engine.probeUsername, engine.probePassword)
        var country = ""
        while (true) {
            val vpnDelay = if (params.vpnActive) latencyMs(engine.probeSocksPort, vpnProbeUrl, credentials) else null
            val byedpiDelay = params.byeDpiPort?.let { byeDpiLatencyMs(it) }
            if (vpnDelay != null && country.isEmpty()) {
                country = resolveCountry(engine.probeSocksPort, credentials) ?: country
            }
            val snapshot = HealthSnapshot(
                vpnActive = params.vpnActive,
                vpnDelayMs = vpnDelay,
                vpnCountry = country,
                byedpiActive = params.byeDpiPort != null,
                byedpiDelayMs = byedpiDelay,
                fpError = params.isFingerprintError()
            )
            emit(snapshot)
            delay((if (snapshot.vpnHealthy) HEALTH_INTERVAL_MS else HEALTH_RETRY_MS).milliseconds)
        }
    }

    private val byeDpiCandidates = listOf(byeDpiProbeUrl) + ByeDpiSites.QUICK.sites.map { "https://$it" }
    private var lastGoodByeDpi = 0

    private suspend fun byeDpiLatencyMs(socksPort: Int): Int? {
        repeat(BYEDPI_ATTEMPTS) { attempt ->
            val index = (lastGoodByeDpi + attempt) % byeDpiCandidates.size
            val url = byeDpiCandidates[index]
            val start = System.nanoTime()
            val reached = SocksHttp.request(socksPort, url, "HEAD", BYEDPI_TIMEOUT_MS, null) { it.responseCode } != null
            if (reached) {
                lastGoodByeDpi = index
                return ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }
        lastGoodByeDpi = (lastGoodByeDpi + BYEDPI_ATTEMPTS) % byeDpiCandidates.size
        return null
    }

    private suspend fun latencyMs(socksPort: Int, url: String, credentials: SocksCredentials?): Int? {
        val start = System.nanoTime()
        return SocksHttp.request(socksPort, url, "GET", PROBE_TIMEOUT_MS, credentials) { connection ->
            val code = connection.responseCode
            connection.drain()
            if (code in 200..399) ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1) else null
        }
    }

    private suspend fun resolveCountry(socksPort: Int, credentials: SocksCredentials): String? =
        SocksHttp.request(socksPort, GEO_URL, "GET", PROBE_TIMEOUT_MS, credentials) { connection ->
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            COUNTRY_REGEX.find(body)?.groupValues?.get(1)?.uppercase()?.takeIf { it != UNKNOWN_COUNTRY }
        }

    private fun java.net.HttpURLConnection.drain() {
        runCatching { (if (responseCode >= 400) errorStream else inputStream)?.use { it.readBytes() } }
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
