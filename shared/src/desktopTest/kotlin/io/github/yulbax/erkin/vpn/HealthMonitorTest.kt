package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HealthMonitorTest {

    @Test
    fun aDeadByeDpiChannelNeverReloadsAWorkingVpn() = runBlocking {
        val (reloads, byedpiChecks) = run(vpnDelayMs = 120, byedpiUp = false)

        assertEquals(0, reloads)
        assertTrue("the site list check must run even while ByeDPI is down", byedpiChecks >= 1)
    }

    @Test
    fun aDeadVpnChannelStillTriggersAReload() = runBlocking {
        val (reloads, _) = run(vpnDelayMs = null, byedpiUp = true)

        assertTrue(reloads >= 1)
    }

    @Test
    fun aDeadVpnIsReportedEvenWhileByeDpiStillWorks() {
        assertEquals(VpnState.VpnDown, HealthSnapshot(true, null, true, "", true, true, HealthChannel.VPN).state)
        assertEquals(VpnState.Connected(0), HealthSnapshot(true, null, false, "", true, true, HealthChannel.BYEDPI).state)
        assertEquals(VpnState.Verifying, HealthSnapshot(true, null, false, "", false, false, HealthChannel.VPN).state)
        assertEquals(VpnState.Connected(80), HealthSnapshot(true, 80, true, "", true, false, HealthChannel.VPN).state)
        assertEquals(VpnState.Connected(0), HealthSnapshot(false, null, false, "", true, true, HealthChannel.BYEDPI).state)
    }

    @Test
    fun boundServersThatDoNotAnswerAreReportedWithoutReloadingTheVpn() = runBlocking {
        val states = VpnStateRepository()
        val reloads = AtomicInteger()
        val engine = object : VpnEngine by FakeEngine {
            override suspend fun measureDelay(tag: String): Int? = if (tag == "p2") null else 40
        }
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                emit(HealthSnapshot(true, 50, true, "", false, false, HealthChannel.VPN))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = HealthMonitor(states, SilentLog, probe)
        monitor.start(
            scope,
            HealthParams(
                probe = ProbeParams(engine, byeDpiPort = null, vpnActive = true, serverTags = mapOf(2L to "p2", 3L to "p3")),
                onRefreshSubscription = {},
                onRecoveryReload = { reloads.incrementAndGet() },
                onByedpiChanged = {}
            )
        )
        val down = withTimeoutOrNull(2_000) { states.stats.first { it.unreachableServers.isNotEmpty() }.unreachableServers }
        monitor.stop()
        scope.cancel()

        assertEquals(setOf(2L), down)
        assertEquals(0, reloads.get())
    }

    private suspend fun run(vpnDelayMs: Int?, byedpiUp: Boolean): Pair<Int, Int> {
        val reloads = AtomicInteger()
        val byedpiChecks = AtomicInteger()
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                repeat(SNAPSHOTS) {
                    emit(HealthSnapshot(true, vpnDelayMs, true, "", true, byedpiUp, HealthChannel.VPN))
                    emit(HealthSnapshot(true, vpnDelayMs, true, "", true, byedpiUp, HealthChannel.BYEDPI))
                    delay(5)
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = HealthMonitor(VpnStateRepository(), SilentLog, probe)
        monitor.start(
            scope,
            HealthParams(
                probe = ProbeParams(FakeEngine, byeDpiPort = 1, vpnActive = true),
                onRefreshSubscription = {},
                onRecoveryReload = { reloads.incrementAndGet() },
                onByedpiChanged = { byedpiChecks.incrementAndGet() }
            )
        )
        withTimeoutOrNull(2_000) { while (true) delay(50) }
        monitor.stop()
        scope.cancel()
        return reloads.get() to byedpiChecks.get()
    }

    private object FakeEngine : VpnEngine {
        override val probeSocksPort: Int = 0
        override val probeUsername: String = ""
        override val probePassword: String = ""
        override fun start(config: EngineConfig) = Unit
        override fun reloadRouting(config: EngineConfig) = Unit
        override suspend fun measureVpnDelay(): Int? = null
        override suspend fun measureDelay(tag: String): Int? = null
        override fun stop() = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val SNAPSHOTS = 12
    }
}
