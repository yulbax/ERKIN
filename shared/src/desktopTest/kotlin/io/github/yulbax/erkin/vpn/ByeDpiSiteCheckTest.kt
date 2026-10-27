package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ByeDpiDns
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.DnsTransport
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ByeDpiSiteCheckTest {

    @Test
    fun theIndicatorChecksTheQuickSitesThroughTheByeDpiResolver() = runBlocking {
        var asked: Triple<Int, String, List<String>>? = null
        val reachableHost = ByeDpiSites.QUICK.first().sites.first()
        val check = ByeDpiSiteCheck { port, resolver, sites, _, _ ->
            asked = Triple(port, resolver, sites)
            mapOf(reachableHost to true)
        }

        val result = check.check(1080, NetworkOptions(byeDpiDns = ByeDpiDns("9.9.9.9", DnsTransport.UDP)))

        assertEquals(Triple(1080, "udp://9.9.9.9", ByeDpiSites.QUICK_HOSTS.toList().sorted()), asked?.let { it.copy(third = it.third.sorted()) })
        assertEquals(ByeDpiReachability(1, ByeDpiSites.QUICK_HOSTS.size), result)
    }
}
