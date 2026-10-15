package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.vpn.ProxyLatencyTester
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihomocore.Mihomocore

class MihomoLatencyTester(private val appContext: Context) : ProxyLatencyTester {
    override suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeoutMs: Int): String =
        runInterruptible(Dispatchers.IO) {
            MihomoRuntime.ensureSetup(appContext)
            Mihomocore.testProxies(MihomoConfigBuilder.proxiesForTest(proxies, options), url, timeoutMs)
        }
}
