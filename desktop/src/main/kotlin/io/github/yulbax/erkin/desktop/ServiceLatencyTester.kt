package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.ProxyLatencyTester
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class ServiceLatencyTester(private val service: DesktopService) : ProxyLatencyTester {
    override suspend fun measure(proxies: List<EngineProxy>, options: NetworkOptions, url: String, timeoutMs: Int): String =
        runInterruptible(Dispatchers.IO) { service.connect().urlTest(MihomoConfigBuilder.proxiesForTest(proxies, options), url, timeoutMs) }
}
