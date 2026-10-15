package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.ProxyLatencyTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

class ServiceLatencyTester(private val service: DesktopService) : ProxyLatencyTester {
    override suspend fun measure(config: String, url: String, timeoutMs: Int): String =
        runInterruptible(Dispatchers.IO) { service.connect().urlTest(config, url, timeoutMs) }
}
