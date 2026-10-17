package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.rulelist.InstalledRuleList
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.ByeDpiProcess
import io.github.yulbax.erkin.vpn.core.ByeDpiQualityCheck
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class ReloadOutcome { Done, SelectionRemoved }

class VpnSession(
    private val engine: VpnEngine,
    private val byeDpi: ByeDpiProcess,
    private val store: VpnSessionStore,
    private val stateRepository: VpnStateRepository,
    private val health: HealthMonitor,
    private val quality: ByeDpiQualityCheck,
    private val log: AppLog,
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val requestWork: (Command) -> Unit,
    private val onByeDpiExit: (run: Long, code: Int) -> Unit,
    private val geoIpReady: () -> Boolean = { false },
    private val ruleLists: (String) -> InstalledRuleList? = { null }
) {
    private enum class Phase { Idle, Starting, Running, Stopping }

    @Volatile private var phase = Phase.Idle
    @Volatile var generation = 0L
        private set

    private var appliedConfig: AppliedConfig? = null
    private var byeDpiArgs: List<String> = ByeDpiArgs.DEFAULT
    private var byeDpiPort = 0
    private var byeDpiRun = 0L
    private var byeDpiStartedAt: TimeMark? = null
    private var rapidByeDpiExits = 0
    private var routeWatchJob: Job? = null
    private var byeDpiCheckJob: Job? = null

    val isIdle: Boolean get() = phase == Phase.Idle
    val configName: String get() = appliedConfig?.configName.orEmpty()
    private val isRunning: Boolean get() = phase == Phase.Running

    private val byeDpiProbePort: Int get() = engine.byeDpiProbePort(byeDpiPort)

    suspend fun start(systemInitiated: Boolean) {
        if (phase != Phase.Idle) return
        phase = Phase.Starting
        generation++
        rapidByeDpiExits = 0
        log.i(TAG, "start requested system=$systemInitiated")
        stateRepository.update(VpnState.Connecting)
        appliedConfig = null
        store.refreshProfiles()
        val inputs = store.load()
        byeDpiArgs = ByeDpiArgs.parse(inputs.settings.byeDpiArgs)
        byeDpiPort = freeLoopbackPort()
        val config = EngineConfigComposer.compose(inputs, ownPackage, byeDpiPort, geoIpReady(), ruleLists)
        log.i(
            TAG,
            "config: server='${config.configName}' profiles=${config.engineConfig.proxies.size} " +
                "vpnApps=${config.engineConfig.vpnPackages.size} " +
                "byedpiApps=${config.engineConfig.byeDpiPackages.size} " +
                "byedpiPort=$byeDpiPort"
        )
        if (config.needsByeDpi) startByeDpi()
        engine.start(config.engineConfig)
        commit(config)
        phase = Phase.Running
        log.i(TAG, "engine started")
        stateRepository.update(VpnState.Verifying)
        watchRouting()
        startHealth()
    }

    suspend fun reload(): ReloadOutcome {
        if (!isRunning) return ReloadOutcome.Done
        val inputs = store.load()
        val current = checkNotNull(appliedConfig) { "VPN has no applied config" }
        val desired = runCatching { EngineConfigComposer.compose(inputs, ownPackage, byeDpiPort, geoIpReady(), ruleLists) }.getOrElse { error ->
            if (inputs.selected == null) {
                log.i(TAG, "selected server removed and nothing else to run; stopping")
                return ReloadOutcome.SelectionRemoved
            }
            throw error
        }
        val serverSwitched = desired.selectedTag != current.selectedTag
        if (!desired.isStructuralChangeFrom(current) && !serverSwitched) {
            if (desired.configName != current.configName) commit(desired)
            return ReloadOutcome.Done
        }

        resetChannels()
        applyReload(desired)
        startHealth()
        return ReloadOutcome.Done
    }

    suspend fun recover() {
        if (!isRunning) return
        log.i(TAG, "vpn channel down; reloading service")
        resetChannels()
        applyReload(EngineConfigComposer.compose(store.load(), ownPackage, byeDpiPort, geoIpReady(), ruleLists))
        startHealth()
    }

    fun checkByeDpi() {
        if (!isRunning || !byeDpi.isRunning) return
        if (byeDpiCheckJob?.isActive == true) return
        val port = byeDpiProbePort
        val checkGeneration = generation
        byeDpiCheckJob = scope.launch {
            stateRepository.updateStats { it.copy(byedpiChecking = true) }
            try {
                val result = quality.check(port)
                ensureActive()
                if (isRunning && generation == checkGeneration && byeDpi.isRunning) {
                    stateRepository.updateStats {
                        it.copy(
                            byedpiChecking = false,
                            byedpiReachable = result.reachable,
                            byedpiTotal = result.total
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                log.w(TAG, "ByeDPI quality check failed", t)
                if (isRunning && generation == checkGeneration) {
                    stateRepository.updateStats { it.copy(byedpiChecking = false) }
                }
            }
        }
    }

    fun onByeDpiExited(run: Long, code: Int) {
        if (run != byeDpiRun || !byeDpi.isRunning) return
        log.w(TAG, "byedpi exited unexpectedly code=$code")
        runCatching { byeDpi.stop() }
        stateRepository.byeDpiStopped()

        val config = appliedConfig ?: return
        if (!isRunning || !config.needsByeDpi) return
        val stable = (byeDpiStartedAt?.elapsedNow() ?: 0.seconds) >= BYEDPI_STABLE_RUNTIME
        rapidByeDpiExits = if (stable) 1 else rapidByeDpiExits + 1
        check(rapidByeDpiExits <= MAX_RAPID_BYEDPI_EXITS) { "ByeDPI repeatedly exited with code $code" }
        startByeDpi()
        startHealth()
    }

    fun onThroughput(uplinkBytesPerSec: Long, downlinkBytesPerSec: Long) {
        if (!isRunning) return
        stateRepository.updateStats { it.copy(uplink = uplinkBytesPerSec, downlink = downlinkBytesPerSec) }
    }

    fun markStopping() {
        if (phase != Phase.Idle) phase = Phase.Stopping
    }

    fun stop(finalState: VpnState) {
        try {
            log.i(TAG, "stop state=${finalState::class.simpleName}")
            phase = Phase.Stopping
            generation++
            runCatching { routeWatchJob?.cancel() }
            routeWatchJob = null
            runCatching { health.stop() }
            runCatching { byeDpiCheckJob?.cancel() }
            byeDpiCheckJob = null
            runCatching { engine.stop() }
                .onFailure { log.w(TAG, "failed to stop engine", it) }
            runCatching { stopByeDpi() }
                .onFailure { log.w(TAG, "failed to stop ByeDPI", it) }
            appliedConfig = null
            runCatching { stateRepository.resetStats() }
            runCatching { stateRepository.update(finalState) }
        } finally {
            phase = Phase.Idle
        }
    }

    private fun commit(config: AppliedConfig) {
        appliedConfig = config
        stateRepository.updateStats {
            it.copy(
                configName = config.configName,
                byeDpiPort = if (config.needsByeDpi) byeDpiProbePort else 0
            )
        }
    }

    private fun startByeDpi() {
        if (byeDpi.isRunning) return
        val run = ++byeDpiRun
        byeDpiStartedAt = TimeSource.Monotonic.markNow()
        byeDpi.start(byeDpiPort, byeDpiArgs) { code -> onByeDpiExit(run, code) }
        stateRepository.updateStats { it.copy(byeDpiPort = byeDpiProbePort) }
    }

    private fun stopByeDpi() {
        stateRepository.byeDpiStopped()
        byeDpi.stop()
    }

    private fun resetChannels() {
        health.stop()
        byeDpiCheckJob?.cancel()
        stateRepository.channelsReconnecting()
        stateRepository.update(VpnState.Verifying)
    }

    private fun applyReload(config: AppliedConfig) {
        val startedForReload = config.needsByeDpi && !byeDpi.isRunning
        if (startedForReload) startByeDpi()
        try {
            engine.reloadRouting(config.engineConfig)
        } catch (t: Throwable) {
            if (startedForReload) {
                runCatching { stopByeDpi() }.onFailure { t.addSuppressed(it) }
            }
            throw t
        }
        if (!config.needsByeDpi && byeDpi.isRunning) stopByeDpi()
        commit(config)
    }

    @OptIn(FlowPreview::class)
    private fun watchRouting() {
        routeWatchJob?.cancel()
        routeWatchJob = scope.launch {
            store.routingChanges()
                .debounce(300.milliseconds)
                .collect { requestWork(Command.Reload) }
        }
    }

    private fun startHealth() {
        val config = appliedConfig ?: return
        health.start(
            scope = scope,
            params = HealthParams(
                probe = ProbeParams(
                    engine = engine,
                    byeDpiPort = byeDpiProbePort.takeIf { byeDpi.isRunning },
                    vpnActive = config.vpnActive
                ),
                onRefreshSubscription = { refreshSubscription() },
                onRecoveryReload = { requestWork(Command.Recover) },
                onByedpiChanged = { requestWork(Command.CheckByeDpi) }
            )
        )
    }

    private suspend fun refreshSubscription() {
        val profile = store.selectedSubscription() ?: return
        log.i(TAG, "reconnect threshold reached; refreshing subscription '${profile.name}'")
        when (val result = store.refreshSubscription(profile)) {
            is ProfileOperationResult.Success ->
                if (result.affected == 0) {
                    log.w(TAG, "subscription no longer lists this server; keeping it unchanged")
                } else {
                    log.i(TAG, "subscription refreshed; engine will reload with the new server")
                }
            ProfileOperationResult.EmptySubscription ->
                log.i(TAG, "subscription refresh returned no servers")
            is ProfileOperationResult.FetchFailed ->
                log.e(TAG, "subscription refresh failed", result.cause)
            ProfileOperationResult.InvalidLink ->
                log.w(TAG, "subscription refresh rejected an invalid link")
        }
    }

    private companion object {
        const val TAG = "VpnSession"
        val BYEDPI_STABLE_RUNTIME = 30.seconds
        const val MAX_RAPID_BYEDPI_EXITS = 3
    }
}
