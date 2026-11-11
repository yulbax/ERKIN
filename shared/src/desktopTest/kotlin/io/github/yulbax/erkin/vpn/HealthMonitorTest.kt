package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProbeResult
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
import kotlin.time.Duration.Companion.milliseconds

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
    fun theCardIsConnectedOnlyWhenEveryChannelAnswers() {
        val vpn = HealthChannel.VPN
        assertEquals(VpnState.ChannelDown(vpn), HealthSnapshot(true, null, true, "", true, true, true, vpn).state)
        assertEquals(VpnState.ChannelDown(HealthChannel.BYEDPI), HealthSnapshot(true, 80, true, "", true, false, true, vpn).state)
        assertEquals(VpnState.Verifying, HealthSnapshot(true, 80, true, "", true, false, false, vpn).state)
        assertEquals(VpnState.Verifying, HealthSnapshot(true, null, false, "", true, true, true, vpn).state)
        assertEquals(VpnState.Connected(80), HealthSnapshot(true, 80, true, "", true, true, true, vpn).state)
        assertEquals(VpnState.Connected(80), HealthSnapshot(true, 80, true, "", false, false, false, vpn).state)
        assertEquals(VpnState.Connected(0), HealthSnapshot(false, null, false, "", true, true, true, HealthChannel.BYEDPI).state)
    }

    @Test
    fun aMainServerThatKeepsRejectingTheLoginStopsInsteadOfReloading() = runBlocking {
        val rejections = AtomicInteger()
        val reloads = AtomicInteger()
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                repeat(SNAPSHOTS) {
                    emit(HealthSnapshot(true, null, true, "", false, false, true, HealthChannel.VPN, vpnLoginRejected = true))
                    delay(20)
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = HealthMonitor(VpnStateRepository(), SilentLog, probe)
        monitor.start(
            scope,
            HealthParams(
                probe = ProbeParams(FakeEngine, byeDpiPort = null, vpnActive = true),
                onLoginRejected = { rejections.incrementAndGet() },
                onVpnDown = { reloads.incrementAndGet() },
                onVpnUp = {},
                onByedpiChanged = {}
            )
        )
        withTimeoutOrNull(2_000) { while (rejections.get() == 0) delay(10) }
        delay(100)
        monitor.stop()
        scope.cancel()

        assertEquals(1, rejections.get())
        assertEquals(0, reloads.get())
    }

    @Test
    fun theCoreSaysWhetherALoginWasRejected() {
        assertEquals(ProbeResult.Answered(80), ProbeResult.fromWire(80))
        assertEquals(ProbeResult.NoAnswer, ProbeResult.fromWire(-1))
        assertEquals(ProbeResult.LoginRejected, ProbeResult.fromWire(-2))
        assertEquals(ProbeResult.NoAnswer, ProbeResult.fromWire(0))
    }

    @Test
    fun boundServersThatDoNotAnswerAreReportedWithoutReloadingTheVpn() = runBlocking {
        val states = VpnStateRepository()
        val reloads = AtomicInteger()
        val engine = object : VpnEngine by FakeEngine {
            override suspend fun probe(tag: String): ProbeResult = if (tag == "p2") ProbeResult.NoAnswer else ProbeResult.Answered(40)
        }
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                emit(HealthSnapshot(true, 50, true, "", false, false, true, HealthChannel.VPN))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = HealthMonitor(states, SilentLog, probe, serverCheckInterval = 50.milliseconds, serverRecheckDelay = 10.milliseconds)
        monitor.start(
            scope,
            HealthParams(
                probe = ProbeParams(engine, byeDpiPort = null, vpnActive = true, serverTags = mapOf(2L to "p2", 3L to "p3")),
                onLoginRejected = {},
                onVpnDown = { reloads.incrementAndGet() },
                onVpnUp = {},
                onByedpiChanged = {}
            )
        )
        val down = withTimeoutOrNull(2_000) { states.stats.first { it.unreachableServers.isNotEmpty() }.unreachableServers }
        monitor.stop()
        scope.cancel()

        assertEquals(setOf(2L), down)
        assertEquals(0, reloads.get())
    }

    @Test
    fun aSingleMissedCheckDoesNotMarkAServerAsDown() = runBlocking {
        val states = VpnStateRepository()
        val checks = AtomicInteger()
        val engine = object : VpnEngine by FakeEngine {
            override suspend fun probe(tag: String): ProbeResult = if (checks.incrementAndGet() == 1) ProbeResult.NoAnswer else ProbeResult.Answered(40)
        }
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                emit(HealthSnapshot(true, 50, true, "", false, false, true, HealthChannel.VPN))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val monitor = HealthMonitor(states, SilentLog, probe, serverCheckInterval = 50.milliseconds, serverRecheckDelay = 10.milliseconds)
        monitor.start(
            scope,
            HealthParams(
                probe = ProbeParams(engine, byeDpiPort = null, vpnActive = true, serverTags = mapOf(2L to "p2")),
                onLoginRejected = {},
                onVpnDown = {},
                onVpnUp = {},
                onByedpiChanged = {}
            )
        )
        val down = withTimeoutOrNull(500) { states.stats.first { it.unreachableServers.isNotEmpty() } }
        monitor.stop()
        scope.cancel()

        assertEquals(null, down)
        assertTrue(checks.get() >= 2)
    }

    private suspend fun run(vpnDelayMs: Int?, byedpiUp: Boolean): Pair<Int, Int> {
        val reloads = AtomicInteger()
        val byedpiChecks = AtomicInteger()
        val probe = object : HealthProbe {
            override fun observe(params: ProbeParams): Flow<HealthSnapshot> = flow {
                repeat(SNAPSHOTS) {
                    emit(HealthSnapshot(true, vpnDelayMs, true, "", true, byedpiUp, true, HealthChannel.VPN))
                    emit(HealthSnapshot(true, vpnDelayMs, true, "", true, byedpiUp, true, HealthChannel.BYEDPI))
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
                onLoginRejected = {},
                onVpnDown = { reloads.incrementAndGet() },
                onVpnUp = {},
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
        override suspend fun probeVpn(): ProbeResult = ProbeResult.NoAnswer
        override suspend fun probe(tag: String): ProbeResult = ProbeResult.NoAnswer
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
