package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihomocore.Mihomocore

class MihomoSiteTester(private val appContext: Context, private val log: AppLog) : ByeDpiSiteTester {
    override suspend fun test(port: Int, resolver: String, sites: List<String>, timeoutMs: Int, ipv6: Boolean): Map<String, Boolean> =
        runInterruptible(Dispatchers.IO) {
            MihomoRuntime.ensureSetup(appContext, log)
            ByeDpiSiteTester.decode(Mihomocore.testSites(port, resolver, sites.joinToString(" "), timeoutMs, ipv6))
        }
}
