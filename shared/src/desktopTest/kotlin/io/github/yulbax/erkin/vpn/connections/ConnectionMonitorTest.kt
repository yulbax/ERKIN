package io.github.yulbax.erkin.vpn.connections

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.VpnStateRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionMonitorTest {

    @Test
    fun anUnreachableCoreShowsNoActivityWithoutReportingAFailure() = runBlocking {
        val states = VpnStateRepository().apply { update(VpnState.Connected(50)) }
        val reads = AtomicInteger()
        val warnings = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = ConnectionMonitor({ reads.incrementAndGet(); null }, states, CountingLog(warnings), scope)
        try {
            scope.launch { monitor.activity.collect {} }
            withTimeout(3_000) { while (reads.get() < 2) delay(50) }

            assertEquals(emptyList<AppActivity>(), monitor.activity.first())
            assertEquals(0, warnings.get())
        } finally {
            scope.cancel()
        }
    }

    private class CountingLog(private val warnings: AtomicInteger) : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) {
            warnings.incrementAndGet()
        }
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }
}
