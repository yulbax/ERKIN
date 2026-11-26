package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.ProbeResult
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HealthMonitorTest {

    @Test
    fun theCardIsConnectedOnlyWhenEveryChannelAnswers() {
        assertEquals(VpnState.ChannelDown(HealthChannel.VPN), HealthSnapshot(true, null, true, ExitCountry.Unknown, true, true, true).state)
        assertEquals(VpnState.ChannelDown(HealthChannel.BYEDPI), HealthSnapshot(true, 80, true, ExitCountry.Unknown, true, false, true).state)
        assertEquals(VpnState.Verifying, HealthSnapshot(true, 80, true, ExitCountry.Unknown, true, false, false).state)
        assertEquals(VpnState.Verifying, HealthSnapshot(true, null, false, ExitCountry.Unknown, true, true, true).state)
        assertEquals(VpnState.Connected(80), HealthSnapshot(true, 80, true, ExitCountry.Unknown, true, true, true).state)
        assertEquals(VpnState.Connected(80), HealthSnapshot(true, 80, true, ExitCountry.Unknown, false, false, false).state)
        assertEquals(VpnState.Connected(0), HealthSnapshot(false, null, false, ExitCountry.Unknown, true, true, true).state)
    }

    @Test
    fun theCoreSaysWhetherALoginWasRejected() {
        assertEquals(ProbeResult.Answered(80), ProbeResult.fromWire(80))
        assertEquals(ProbeResult.NoAnswer, ProbeResult.fromWire(-1))
        assertEquals(ProbeResult.LoginRejected, ProbeResult.fromWire(-2))
        assertEquals(ProbeResult.AccessDenied, ProbeResult.fromWire(-3))
        assertEquals(ProbeResult.NoAnswer, ProbeResult.fromWire(0))
    }

    @Test
    fun aDeadByeDpiChannelNeverReloadsAWorkingVpn() = runBlocking {
        val run = Run(byeDpiPort = 1, byeDpiUp = false) { 120 }
        run.watch { run.byedpiChanges.get() >= 1 }

        assertEquals(0, run.vpnDowns.get())
        assertTrue(run.byedpiChanges.get() >= 1)
    }

    @Test
    fun aDeadVpnChannelTriggersAReload() = runBlocking {
        val run = Run { FAILED }
        run.watch { run.vpnDowns.get() >= 1 }

        assertTrue(run.vpnDowns.get() >= 1)
    }

    @Test
    fun aMainServerThatKeepsRejectingTheLoginStopsInsteadOfReloading() = runBlocking {
        val run = Run { LOGIN_REJECTED }
        run.watch { run.rejections.get() >= 1 }
        delay(200)

        assertEquals(1, run.rejections.get())
        assertEquals(0, run.vpnDowns.get())
    }

    @Test
    fun boundServersThatDoNotAnswerAreReportedWithoutReloadingTheVpn() = runBlocking {
        val run = Run { id -> if (id == 2L) FAILED else 40 }
        val stats = run.watch { run.states.stats.value.unreachableServers.isNotEmpty() }
            .let { run.states.stats.value }

        assertEquals(setOf(2L), stats.unreachableServers)
        assertEquals(0, run.vpnDowns.get())
        assertEquals(setOf(1L, 2L, 3L), run.results.results.value.keys)
    }

    @Test
    fun aSingleMissedCheckDoesNotMarkABoundServerUnreachable() = runBlocking {
        val checks = AtomicInteger()
        val run = Run { id -> if (id == 2L && checks.incrementAndGet() == 1) FAILED else 40 }
        val reported = run.watch(timeoutMs = 500) { run.states.stats.value.unreachableServers.isNotEmpty() }

        assertEquals(false, reported)
        assertEquals(emptySet<Long>(), run.states.stats.value.unreachableServers)
        assertTrue(checks.get() >= 2)
    }

    @Test
    fun aBoundServerThatRejectsTheLoginIsShownAsALoginErrorWithoutStoppingTheVpn() = runBlocking {
        val run = Run { id -> if (id == 3L) LOGIN_REJECTED else 40 }
        run.watch { run.results.results.value[3L] == ProbeResult.LoginRejected }

        assertEquals(ProbeResult.LoginRejected, run.results.results.value[3L])
        assertEquals(0, run.rejections.get())
    }

    @Test
    fun theServersTheSystemRefusesToReachAreListedWhetherActiveOrBound() = runBlocking {
        val run = Run { id -> if (id == 2L) 40 else ACCESS_DENIED }
        run.watch { run.states.stats.value.deniedServers == setOf(1L, 3L) }

        assertEquals(setOf(1L, 3L), run.states.stats.value.deniedServers)
    }

    @Test
    fun aServerLeavesTheRefusedListOnceItAnswers() = runBlocking {
        val allowed = AtomicBoolean(false)
        val run = Run { if (allowed.get()) 40 else ACCESS_DENIED }
        run.watch { run.states.stats.value.deniedServers.isNotEmpty() }
        allowed.set(true)
        run.watch { run.states.stats.value.deniedServers.isEmpty() }

        assertEquals(emptySet<Long>(), run.states.stats.value.deniedServers)
    }

    @Test
    fun anUnreachableBoundServerIsNotRetriedQuicklyForever() = runBlocking {
        val checks = ConcurrentHashMap<Long, AtomicInteger>()
        val run = Run(checkInterval = 60.seconds) { id ->
            checks.getOrPut(id) { AtomicInteger() }.incrementAndGet()
            if (id == 2L) FAILED else 40
        }
        run.watch(timeoutMs = 500) { false }

        assertEquals(2, checks.getValue(2).get())
    }

    @Test
    fun theCardShowsTheLatencyOfTheActiveServer() = runBlocking {
        val run = Run { 40 }
        run.watch { run.states.state.value == VpnState.Connected(40) }

        assertEquals(VpnState.Connected(40), run.states.state.value)
        assertTrue(run.states.stats.value.vpnUp)
    }

    @Test
    fun theExitCountryIsAskedForAlongsideTheFirstCheckNotAfterIt() = runBlocking {
        val run = Run(hangOn = 1L) { 40 }
        run.watch { run.countryAsked.get() > 0 }

        assertEquals(1, run.countryAsked.get())
        assertFalse(run.states.stats.value.vpnUp)
    }

    @Test
    fun theExitCountryIsPendingUntilItIsFoundOrGivenUp() = runBlocking {
        val found = Run(country = { "NL" }) { 40 }
        found.watch { found.states.stats.value.vpnCountry == ExitCountry.Known("NL") }
        val lost = Run { 40 }
        lost.watch { lost.states.stats.value.vpnCountry == ExitCountry.Unknown }

        assertEquals(ExitCountry.Known("NL"), found.states.stats.value.vpnCountry)
        assertEquals(ExitCountry.Unknown, lost.states.stats.value.vpnCountry)
    }

    @Test
    fun theWatchMeasuresOnlyServersLoadedInTheRunningTunnel() = runBlocking {
        val run = Run { 40 }
        run.watch { run.runningOnlyFlags.size >= 3 }

        assertTrue(run.runningOnlyFlags.isNotEmpty())
        assertTrue(run.runningOnlyFlags.all { it })
    }

    @Test
    fun closingTheMonitorForgetsTheWatchedServersButStoppingKeepsThem() = runBlocking {
        val stopped = Run { 40 }
        stopped.watch { stopped.results.results.value.keys == setOf(1L, 2L, 3L) }
        val closed = Run(closeAtEnd = true) { 40 }
        closed.watch { closed.results.results.value.keys == setOf(1L, 2L, 3L) }

        assertEquals(setOf(1L, 2L, 3L), stopped.results.results.value.keys)
        assertEquals(emptySet<Long>(), closed.results.results.value.keys)
    }

    @Test
    fun theActiveServerKeepsItsCadenceWhileABoundServerHangs() = runBlocking {
        val run = Run(hangOn = 2L) { 40 }
        run.watch { run.measured.getValue(1).get() >= 3 }

        assertTrue(run.measured.getValue(1).get() >= 3)
        assertEquals(1, run.measured.getValue(2).get())
    }

    private class Run(
        private val byeDpiPort: Int? = null,
        private val country: suspend () -> String? = { null },
        private val byeDpiUp: Boolean = true,
        private val checkInterval: Duration = 50.milliseconds,
        private val closeAtEnd: Boolean = false,
        private val hangOn: Long? = null,
        private val delayOf: (Long) -> Int
    ) {
        val states = VpnStateRepository()
        val results = ProbeResults()
        val vpnDowns = AtomicInteger()
        val rejections = AtomicInteger()
        val byedpiChanges = AtomicInteger()
        val runningOnlyFlags = ConcurrentLinkedQueue<Boolean>()
        val countryAsked = AtomicInteger()
        val measured = ConcurrentHashMap<Long, AtomicInteger>().apply { (1L..3L).forEach { put(it, AtomicInteger()) } }

        suspend fun watch(timeoutMs: Long = 2_000, until: () -> Boolean): Boolean {
            val latency = ServerLatencyProbe(Store) { proxies, _, _, _, _, runningOnly ->
                runningOnlyFlags += runningOnly
                val tag = proxies.single().tag
                val id = checkNotNull(ProxyTag.profileId(tag))
                measured.getValue(id).incrementAndGet()
                if (id == hangOn) awaitCancellation()
                """{"$tag":${delayOf(id)}}"""
            }
            val monitor = HealthMonitor(
                states,
                SilentLog,
                ServerChecks(latency, results, SilentLog),
                Channels(byeDpiUp) {
                    countryAsked.incrementAndGet()
                    country()
                },
                checkInterval = checkInterval,
                retryDelay = 10.milliseconds
            )
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            monitor.start(
                scope,
                HealthParams(
                    engine = FakeEngine,
                    byeDpiPort = byeDpiPort,
                    vpnActive = true,
                    activeTag = "p1",
                    activeServer = 1,
                    boundServers = setOf(2, 3),
                    onVpnFailing = {},
                    onVpnDown = { vpnDowns.incrementAndGet() },
                    onLoginRejected = { rejections.incrementAndGet() },
                    onVpnUp = {},
                    onByedpiChanged = { byedpiChanges.incrementAndGet() }
                )
            )
            val reached = withTimeoutOrNull(timeoutMs) {
                while (!until()) delay(10)
                true
            }
            if (closeAtEnd) monitor.close() else monitor.stop()
            scope.cancel()
            return reached == true
        }
    }

    private class Channels(private val byeDpiUp: Boolean, private val lookUp: suspend () -> String?) : ChannelProbe {
        override suspend fun byeDpiReachable(port: Int): Boolean = byeDpiUp
        override suspend fun country(engine: VpnEngine): String? = lookUp()
    }

    private object Store : VpnSessionStore {
        private val servers = (1L..3L).map { ProfileEntity(id = it, name = "s$it") }
        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(servers.first(), servers, SettingsEntity(), emptyList())
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedGroup(): ServerGroupEntity? = null
        override suspend fun refreshSubscription(url: String): ProfileOperationResult = ProfileOperationResult.EmptySubscription
        override suspend fun refreshDueSubscriptions() = Unit
        override suspend fun select(profile: ProfileEntity) = Unit
    }

    private object FakeEngine : VpnEngine {
        override val probeSocksPort: Int = 0
        override val probeUsername: String = ""
        override val probePassword: String = ""
        override fun start(config: EngineConfig) = Unit
        override fun reloadRouting(config: EngineConfig) = Unit
        override fun stop() = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val FAILED = -1
        const val LOGIN_REJECTED = -2
        const val ACCESS_DENIED = -3
    }
}
