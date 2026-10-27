package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ByeDpiDns
import io.github.yulbax.erkin.vpn.core.ByeDpiQualityCheck
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import io.github.yulbax.erkin.vpn.core.NetworkOptions

class ByeDpiSiteCheck(private val tester: ByeDpiSiteTester) : ByeDpiQualityCheck {
    suspend fun results(socksPort: Int, groups: List<SiteGroup>, dns: ByeDpiDns, ipv6: Boolean): List<SiteResult> {
        val reachable = tester.test(socksPort, dns.nameserver(), groups.flatMap { it.sites }.distinct(), TIMEOUT_MS, ipv6)
        return groups.resultsBy { reachable[it] == true }
    }

    override suspend fun check(socksPort: Int, network: NetworkOptions): ByeDpiReachability =
        results(socksPort, ByeDpiSites.QUICK, network.byeDpiDns, network.ipv6Mode.ipv6Enabled).reachability()

    companion object {
        private const val TIMEOUT_MS = 5_000

        fun unreachable(groups: List<SiteGroup>): List<SiteResult> = groups.resultsBy { false }

        private fun List<SiteGroup>.resultsBy(reachable: (String) -> Boolean): List<SiteResult> =
            flatMap { group -> group.sites.map { SiteResult(it, group.name, reachable(it)) } }
    }
}
