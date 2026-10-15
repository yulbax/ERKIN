package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineListener
import io.github.yulbax.erkin.vpn.core.FingerprintErrorWatch
import io.github.yulbax.erkin.vpn.core.VPN_DELAY_TIMEOUT_MS
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.vpn.singbox.AppRouting
import io.github.yulbax.erkin.vpn.singbox.ConfigBuilder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServiceEngine(
    private val service: DesktopService,
    private val directProcesses: List<String>,
    private val delayProbeUrl: String,
    private val listener: EngineListener,
    private val log: AppLog
) : VpnEngine {

    override val probeSocksPort: Int = freeLoopbackPort()
    override val probeUsername: String = randomToken()
    override val probePassword: String = randomToken()

    @Volatile private var client: ServiceClient? = null
    private var traffic: ScheduledExecutorService? = null
    private val fingerprintErrors = FingerprintErrorWatch(::boxLog)

    @Synchronized
    override fun start(config: EngineConfig) {
        check(client == null) { "sing-box is already running" }
        launch(config)
    }

    @Synchronized
    override fun reloadRouting(config: EngineConfig) {
        terminate()
        launch(config)
    }

    override suspend fun measureVpnDelay(): Int? = withContext(Dispatchers.IO) {
        client?.runCatching { delay(ConfigBuilder.HEALTH_GROUP_TAG, delayProbeUrl, VPN_DELAY_TIMEOUT_MS) }?.getOrNull()
    }

    override fun hasFingerprintError(): Boolean = fingerprintErrors.hasError()

    @Synchronized
    override fun stop() {
        terminate()
    }

    private fun launch(config: EngineConfig) {
        val connected = service.connect()
        fingerprintErrors.markCoreStart()
        connected.start(buildConfig(config))
        client = connected
        log.i(TAG, "sing-box started by the ERKIN service ${service.hello?.version.orEmpty()}")
        traffic = sampleTraffic(connected)
    }

    private fun boxLog(): File? = service.hello?.workDir?.let { File(it, "box.log") }

    private fun sampleTraffic(connected: ServiceClient): ScheduledExecutorService {
        var last: ServiceClient.Traffic? = null
        return Executors.newSingleThreadScheduledExecutor { Thread(it, "sing-box-traffic").apply { isDaemon = true } }
            .apply {
                scheduleAtFixedRate({
                    val now = runCatching { connected.traffic() }.getOrNull() ?: return@scheduleAtFixedRate
                    last?.let { listener.onThroughput(now.up - it.up, now.down - it.down) }
                    last = now
                }, 0, 1, TimeUnit.SECONDS)
            }
    }

    private fun terminate() {
        val stopping = client ?: return
        client = null
        traffic?.shutdownNow()
        traffic = null
        if (stopping.isOpen) runCatching { stopping.stop() }.onFailure { log.w(TAG, "sing-box stop failed", it) }
    }

    private fun buildConfig(config: EngineConfig): String = ConfigBuilder.build(
        proxies = config.proxies,
        activeProxyTag = config.activeProxyTag,
        byeDpiPackages = config.byeDpiPackages,
        vpnPackages = config.vpnPackages,
        tunneledPackages = config.tunneledPackages,
        byeDpiPort = config.byeDpiSocksPort,
        probePort = probeSocksPort,
        probeUser = probeUsername,
        probePass = probePassword,
        options = config.network,
        routing = AppRouting.DesktopProcesses(directProcesses),
        directPaths = config.directPaths
    )

    private companion object {
        const val TAG = "ServiceEngine"
    }
}
