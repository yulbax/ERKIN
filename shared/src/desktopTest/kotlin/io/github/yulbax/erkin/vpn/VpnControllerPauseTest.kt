package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.VpnEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnControllerPauseTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val states = VpnStateRepository()
    private val engine = FakeEngine()
    private val host = FakeHost()
    private val controller = VpnController(scope, VpnCommandBus(), states, host, SilentLog) { controller ->
        VpnSession(
            engine = engine,
            byeDpi = FakeByeDpi,
            store = FakeStore,
            stateRepository = states,
            health = HealthMonitor(states, SilentLog, NoHealth),
            quality = { _, _ -> ByeDpiReachability(0, 0) },
            log = SilentLog,
            ownPackage = "own",
            scope = scope,
            requestWork = controller::requestWork,
            onByeDpiExit = controller::onByeDpiExit
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun awaitState(expected: (VpnState) -> Boolean) = withTimeout(TIMEOUT_MS) {
        states.state.first(expected)
    }

    private suspend fun connect() {
        controller.start(token = null, systemInitiated = false)
        awaitState { it == VpnState.Verifying }
    }

    @Test
    fun pauseStopsTheEngineButKeepsTheHostAndResumeStartsAgain() = runBlocking {
        controller.launch()
        connect()

        controller.pause()
        awaitState { it == VpnState.Paused }
        assertEquals(1, engine.stops)
        assertEquals(1, host.pauses)
        assertEquals(0, host.stops)

        connect()
        assertEquals(2, engine.starts)
    }

    @Test
    fun stoppingWhilePausedDisconnectsAndStopsTheHost() = runBlocking {
        controller.launch()
        connect()
        controller.pause()
        awaitState { it == VpnState.Paused }

        controller.stop(token = null, force = false)
        awaitState { it == VpnState.Disconnected }
        assertEquals(1, host.stops)
    }

    @Test
    fun pauseIsIgnoredWhenThePlatformKeepsTheVpnOnAndWhenIdle() = runBlocking {
        controller.launch()
        controller.pause()
        host.allowsStop = false
        connect()

        controller.pause()
        controller.stop(token = null, force = true)
        awaitState { it == VpnState.Disconnected }
        assertEquals(1, engine.starts)
        assertEquals(1, engine.stops)
        assertEquals(0, host.pauses)
    }

    @Test
    fun shuttingDownWhilePausedLeavesTheAppDisconnected() = runBlocking {
        controller.launch()
        connect()
        controller.pause()
        awaitState { it == VpnState.Paused }

        controller.shutdown()
        assertEquals(VpnState.Disconnected, states.state.value)
    }

    private class FakeHost : VpnHost {
        @Volatile var allowsStop = true
        @Volatile var pauses = 0
        @Volatile var stops = 0

        override fun onSessionStarting() = Unit
        override fun onSessionStarted() = Unit
        override fun allowsUserStop(): Boolean = allowsStop
        override fun onSessionStopped(stopToken: Int?, stopHost: Boolean) {
            stops++
        }
        override fun onSessionPaused() {
            pauses++
        }
    }

    private class FakeEngine : VpnEngine {
        @Volatile var starts = 0
        @Volatile var stops = 0

        override val probeSocksPort = 0
        override val probeUsername = ""
        override val probePassword = ""
        override fun start(config: EngineConfig) {
            starts++
        }
        override fun reloadRouting(config: EngineConfig) = Unit
        override suspend fun measureVpnDelay(): Int? = null
        override fun stop() {
            stops++
        }
    }

    private object NoHealth : HealthProbe {
        override fun observe(params: ProbeParams): Flow<HealthSnapshot> = emptyFlow()
    }

    private object FakeByeDpi : ByeDpiProcess {
        override val isRunning = false
        override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) = Unit
        override fun stop() = Unit
    }

    private object FakeStore : VpnSessionStore {
        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(
            selected = null,
            profiles = emptyList(),
            settings = SettingsEntity(),
            apps = listOf(App("org.ads", "Ads", isSystemApp = false, connectionType = ConnectionType.BLOCK))
        )
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedSubscription(): SubscriptionEntity? = null
        override suspend fun refreshSubscription(url: String): ProfileOperationResult = error("unused")
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
