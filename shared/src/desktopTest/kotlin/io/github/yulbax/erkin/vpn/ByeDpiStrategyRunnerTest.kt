package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiDns
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiSiteTester
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByeDpiStrategyRunnerTest {

    @Test
    fun stoppingKillsTheProcessesSoABlockedSiteTestReturnsAtOnce() = runBlocking {
        val processes = mutableListOf<FakeProcess>()
        val blocked = CountDownLatch(2)
        val runner = ByeDpiStrategyRunner(
            newProcess = { FakeProcess().also { synchronized(processes) { processes += it } } },
            siteCheck = ByeDpiSiteCheck(BlockingTester(processes, blocked)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            log = SilentLog
        )

        runner.start(StrategyRunKind.Saved, listOf("-a", "-b"), ByeDpiDns.DEFAULT, ipv6 = false)
        assertTrue(blocked.await(5, TimeUnit.SECONDS))
        val stoppedAt = System.nanoTime()
        runner.stop()
        withTimeout(2_000) { runner.run.first { it == null } }

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stoppedAt) < BLOCK_MS)
        assertEquals(2, processes.size)
        assertTrue(processes.none { it.isRunning })
    }

    @Test
    fun aFinishedRunClearsItsState(): Unit = runBlocking {
        val runner = ByeDpiStrategyRunner(
            newProcess = { FakeProcess() },
            siteCheck = ByeDpiSiteCheck { _, _, sites, _, _ -> sites.associateWith { true } },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            log = SilentLog
        )

        runner.start(StrategyRunKind.Saved, listOf("-a", "-b"), ByeDpiDns.DEFAULT, ipv6 = false)
        withTimeout(5_000) { runner.run.first { it == null } }
    }

    private class FakeProcess : ByeDpiProcess {
        private var socket: ServerSocket? = null
        val stopped = CountDownLatch(1)
        var port = 0

        override val isRunning: Boolean get() = socket != null

        override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) {
            this.port = port
            socket = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
        }

        @Synchronized
        override fun stop() {
            socket?.close()
            socket = null
            stopped.countDown()
        }
    }

    private class BlockingTester(
        private val processes: List<FakeProcess>,
        private val blocked: CountDownLatch
    ) : ByeDpiSiteTester {
        override suspend fun test(port: Int, resolver: String, sites: List<String>, timeoutMs: Int, ipv6: Boolean): Map<String, Boolean> {
            val process = synchronized(processes) { processes.first { it.port == port } }
            blocked.countDown()
            process.stopped.await(BLOCK_MS, TimeUnit.MILLISECONDS)
            return emptyMap()
        }
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val BLOCK_MS = 10_000L
    }
}
