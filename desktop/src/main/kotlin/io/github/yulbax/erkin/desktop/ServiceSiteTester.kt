package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class ServiceSiteTester(private val service: DesktopService) : ByeDpiSiteTester {
    override suspend fun test(port: Int, resolver: String, sites: List<String>, timeoutMs: Int, ipv6: Boolean): Map<String, Boolean> =
        runInterruptible(Dispatchers.IO) {
            ByeDpiSiteTester.decode(service.connect().testSites(port, resolver, sites, timeoutMs, ipv6))
        }
}
