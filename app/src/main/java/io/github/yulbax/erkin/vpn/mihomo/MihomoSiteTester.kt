package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.vpn.CoreLog
import android.content.Context
import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihomocore.Mihomocore

class MihomoSiteTester(private val appContext: Context, private val coreLog: CoreLog) : ByeDpiSiteTester {
    override suspend fun test(port: Int, resolver: String, sites: List<String>, timeoutMs: Int, ipv6: Boolean): Map<String, Boolean> =
        runInterruptible(Dispatchers.IO) {
            MihomoRuntime.ensureSetup(appContext, coreLog)
            ByeDpiSiteTester.decode(Mihomocore.testSites(port, resolver, sites.joinToString(" "), timeoutMs, ipv6))
        }
}
