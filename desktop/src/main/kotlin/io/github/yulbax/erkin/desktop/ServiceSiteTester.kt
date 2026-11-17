package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

class ServiceSiteTester(private val service: DesktopService) : ByeDpiSiteTester {
    override suspend fun test(port: Int, resolver: String, sites: List<String>, timeout: Duration, ipv6: Boolean): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            ByeDpiSiteTester.decode(service.connect().testSites(port, resolver, sites, timeout, ipv6))
        }
}
