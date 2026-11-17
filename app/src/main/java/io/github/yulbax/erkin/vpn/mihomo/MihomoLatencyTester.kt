package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.ProxyLatencyTester
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import mihomocore.Mihomocore
import kotlin.time.Duration

class MihomoLatencyTester(private val appContext: Context, private val log: AppLog) : ProxyLatencyTester {
    override suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeout: Duration, byeDpiPort: Int?, runningOnly: Boolean): String =
        runInterruptible(Dispatchers.IO) {
            MihomoRuntime.ensureSetup(appContext, log)
            Mihomocore.testProxies(MihomoConfigBuilder.proxiesForTest(proxies, options, byeDpiPort), url, timeout.inWholeMilliseconds.toInt(), options.ipv6Mode.ipv6Enabled, runningOnly)
        }
}
