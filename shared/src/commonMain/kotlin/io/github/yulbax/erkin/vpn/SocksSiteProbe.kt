package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ByeDpiQualityCheck
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object SocksSiteProbe : ByeDpiSiteProbe, ByeDpiQualityCheck {
    private const val TIMEOUT_MS = 3_000
    private const val CONCURRENCY = 12

    override fun probe(socksPort: Int, groups: List<SiteGroup>): Flow<SiteResult> = channelFlow {
        val semaphore = Semaphore(CONCURRENCY)
        coroutineScope {
            groups.forEach { group ->
                group.sites.forEach { host ->
                    launch {
                        semaphore.withPermit {
                            val ok = SocksHttp.request(socksPort, "https://$host", "HEAD", TIMEOUT_MS) { it.responseCode } != null
                            send(SiteResult(host, group.name, ok))
                        }
                    }
                }
            }
        }
    }

    override suspend fun check(socksPort: Int): ByeDpiReachability = ByeDpiReachability(
        reachable = probe(socksPort, listOf(ByeDpiSites.QUICK)).fold(0) { acc, r -> if (r.reachable) acc + 1 else acc },
        total = ByeDpiSites.quickTotal
    )
}
