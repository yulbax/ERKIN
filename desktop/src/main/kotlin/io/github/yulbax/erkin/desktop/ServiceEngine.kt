package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineListener
import io.github.yulbax.erkin.vpn.core.VPN_DELAY_TIMEOUT_MS
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import io.github.yulbax.erkin.vpn.mihomo.MihomoPlatform
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
    private val byeDpiProbeSocksPort: Int = freeLoopbackPort()

    @Volatile private var client: ServiceClient? = null
    @Volatile private var activeProxyTag: String = ""
    private var traffic: ScheduledExecutorService? = null

    @Synchronized
    override fun start(config: EngineConfig) {
        check(client == null) { "mihomo is already running" }
        launch(config)
    }

    @Synchronized
    override fun reloadRouting(config: EngineConfig) {
        terminate()
        launch(config)
    }

    override suspend fun measureVpnDelay(): Int? {
        val tag = activeProxyTag.takeIf { it.isNotEmpty() } ?: return null
        return withContext(Dispatchers.IO) {
            client?.runCatching { delay(tag, delayProbeUrl, VPN_DELAY_TIMEOUT_MS) }?.getOrNull()
        }
    }

    override fun byeDpiProbePort(byeDpiPort: Int): Int = byeDpiProbeSocksPort

    @Synchronized
    override fun stop() {
        terminate()
    }

    private fun launch(config: EngineConfig) {
        val connected = service.connect()
        activeProxyTag = config.activeProxyTag
        connected.start(buildConfig(config), MihomoConfigBuilder.BYEDPI_PROXY, config.network.dnsRemote)
        client = connected
        log.i(TAG, "mihomo started by the ERKIN service ${service.hello?.version.orEmpty()}")
        traffic = sampleTraffic(connected)
    }

    private fun sampleTraffic(connected: ServiceClient): ScheduledExecutorService {
        var last: ServiceClient.Traffic? = null
        return Executors.newSingleThreadScheduledExecutor { Thread(it, "mihomo-traffic").apply { isDaemon = true } }
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
        if (stopping.isOpen) runCatching { stopping.stop() }.onFailure { log.w(TAG, "mihomo stop failed", it) }
    }

    private fun buildConfig(config: EngineConfig): String = MihomoConfigBuilder.build(
        proxies = config.proxies,
        activeProxyTag = config.activeProxyTag,
        byeDpiPackages = config.byeDpiPackages,
        vpnPackages = config.vpnPackages,
        byeDpiPort = config.byeDpiSocksPort,
        probePort = probeSocksPort,
        probeUser = probeUsername,
        probePass = probePassword,
        options = config.network,
        platform = MihomoPlatform.Desktop(directProcesses, SystemDnsDetector.detect(), byeDpiProbeSocksPort),
        directPaths = config.directPaths,
        rules = config.rules
    )

    private companion object {
        const val TAG = "ServiceEngine"
    }
}
