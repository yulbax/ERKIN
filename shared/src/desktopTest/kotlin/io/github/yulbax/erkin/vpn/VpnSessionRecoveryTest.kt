package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.VpnEngine
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSessionRecoveryTest {

    @Test
    fun lookingForAnotherServerDoesNotHoldTheCommandLoop() = runBlocking {
        val refreshing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = Store(refreshing, release)
        val engine = Engine()
        val requested = CopyOnWriteArrayList<Command>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = sessionOf(store, engine, scope, requested)
        try {
            session.start(systemInitiated = false)
            repeat(RecoveryLadder.RECONNECTS_BEFORE_SWITCH) { session.recover() }
            assertEquals(RecoveryLadder.RECONNECTS_BEFORE_SWITCH, engine.reloads.get())

            withTimeout(1_000) { session.recover() }
            withTimeout(1_000) { refreshing.await() }
            session.recover()
            assertEquals(RecoveryLadder.RECONNECTS_BEFORE_SWITCH, engine.reloads.get())
            assertTrue(requested.isEmpty())

            release.complete(Unit)
            withTimeout(2_000) { while (requested.isEmpty()) delay(10) }
            assertEquals(listOf<Command>(Command.Reload), requested.toList())
        } finally {
            session.stop(VpnState.Disconnected)
            scope.cancel()
        }
    }

    @Test
    fun reapplyingTheAppsReloadsTheRoutingOnlyWhileRunning() = runBlocking {
        val store = Store(CompletableDeferred(), CompletableDeferred())
        val engine = Engine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = sessionOf(store, engine, scope, CopyOnWriteArrayList())
        try {
            session.reapplyApps()
            assertTrue(engine.reloaded.isEmpty())

            session.start(systemInitiated = false)
            session.reapplyApps()
            assertEquals(1, engine.reloaded.size)
            assertEquals(listOf("org.vpn"), engine.reloaded.single().tunneledPackages)

            session.stop(VpnState.Disconnected)
            session.reapplyApps()
            assertEquals(1, engine.reloaded.size)
        } finally {
            session.stop(VpnState.Disconnected)
            scope.cancel()
        }
    }

    private fun sessionOf(store: Store, engine: Engine, scope: CoroutineScope, requested: CopyOnWriteArrayList<Command>) = VpnSession(
        engine = engine,
        byeDpi = NoByeDpi,
        store = store,
        stateRepository = VpnStateRepository(),
        health = HealthMonitor(
            VpnStateRepository(),
            SilentLog,
            ServerChecks(ServerLatencyProbe(store) { _, _, _, _, _, _ -> awaitCancellation() }, ProbeResults(), SilentLog),
            SilentChannels
        ),
        failover = ServerFailover(store, ServerChecks(ServerLatencyProbe(store) { _, _, _, _, _, _ -> "{}" }, ProbeResults(), SilentLog), SilentLog),
        quality = { _, _ -> ByeDpiReachability(0, 0) },
        log = SilentLog,
        ownPackage = "io.github.yulbax.erkin",
        scope = scope,
        requestWork = { requested += it },
        onByeDpiExit = { _, _ -> }
    )

    private class Store(private val refreshing: CompletableDeferred<Unit>, private val release: CompletableDeferred<Unit>) : VpnSessionStore {
        private val profile = requireNotNull(LinkParser.parse(VLESS)).let {
            ProfileEntity(id = 1, name = it.name, type = it.protocol.wire, link = VLESS, outboundJson = it.proxyJson(), selected = true, groupId = 7)
        }

        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(profile, listOf(profile), SettingsEntity(), listOf(App("org.vpn", "Vpn", false, ConnectionType.VPN)))
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedGroup() = ServerGroupEntity(id = 7, url = "https://example.com/sub")
        override suspend fun refreshSubscription(url: String): ProfileOperationResult {
            refreshing.complete(Unit)
            release.await()
            return ProfileOperationResult.EmptySubscription
        }
        override suspend fun refreshDueSubscriptions() = Unit
        override suspend fun select(profile: ProfileEntity) = Unit
    }

    private class Engine : VpnEngine {
        val reloads = AtomicInteger()
        val reloaded = CopyOnWriteArrayList<EngineConfig>()
        override val probeSocksPort: Int = 0
        override val probeUsername: String = ""
        override val probePassword: String = ""
        override fun start(config: EngineConfig) = Unit
        override fun reloadRouting(config: EngineConfig) {
            reloads.incrementAndGet()
            reloaded += config
        }
        override fun stop() = Unit
    }

    private object NoByeDpi : ByeDpiProcess {
        override val isRunning: Boolean = false
        override fun start(port: Int, args: List<String>, onUnexpectedExit: (code: Int) -> Unit) = Unit
        override fun stop() = Unit
    }

    private object SilentChannels : ChannelProbe {
        override suspend fun byeDpiReachable(port: Int): Boolean = false
        override suspend fun country(engine: VpnEngine): String? = null
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val VLESS = "vless://id@example.com:443?security=tls#Amsterdam"
    }
}
