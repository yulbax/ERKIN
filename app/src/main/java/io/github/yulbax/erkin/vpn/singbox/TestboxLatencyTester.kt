package io.github.yulbax.erkin.vpn.singbox

import android.content.Context
import io.github.yulbax.erkin.vpn.ProxyLatencyTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import testbox.Testbox

class TestboxLatencyTester(appContext: Context) : ProxyLatencyTester {

    init {
        runCatching { go.Seq.setContext(appContext) }
    }

    override suspend fun measure(config: String, url: String, timeoutMs: Int): String =
        runInterruptible(Dispatchers.IO) { Testbox.urlTest(config, url, timeoutMs, "") }
}
