package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.VpnEngine

class SocksHealthProbe(byeDpiProbeUrl: String) : ChannelProbe {
    private val byeDpiCandidates = listOf(byeDpiProbeUrl) + ByeDpiSites.HEALTH_CHECK.map { "https://$it" }
    private var lastGoodByeDpi = 0

    override suspend fun byeDpiReachable(port: Int): Boolean {
        repeat(BYEDPI_ATTEMPTS) { attempt ->
            val index = (lastGoodByeDpi + attempt) % byeDpiCandidates.size
            if (SocksHttp.request(port, byeDpiCandidates[index], "HEAD", BYEDPI_TIMEOUT_MS, null) { it.responseCode } != null) {
                lastGoodByeDpi = index
                return true
            }
        }
        lastGoodByeDpi = (lastGoodByeDpi + BYEDPI_ATTEMPTS) % byeDpiCandidates.size
        return false
    }

    override suspend fun country(engine: VpnEngine): String? =
        SocksHttp.request(engine.probeSocksPort, GEO_URL, "GET", GEO_TIMEOUT_MS, SocksCredentials(engine.probeUsername, engine.probePassword)) { connection ->
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            COUNTRY_REGEX.find(body)?.groupValues?.get(1)?.uppercase()?.takeIf { it != UNKNOWN_COUNTRY }
        }

    private companion object {
        const val GEO_TIMEOUT_MS = 6_000
        const val BYEDPI_TIMEOUT_MS = 4_000
        const val BYEDPI_ATTEMPTS = 3
        const val GEO_URL = "https://www.cloudflare.com/cdn-cgi/trace"
        const val UNKNOWN_COUNTRY = "XX"
        val COUNTRY_REGEX = Regex("^loc=([A-Za-z]{2})$", RegexOption.MULTILINE)
    }
}
