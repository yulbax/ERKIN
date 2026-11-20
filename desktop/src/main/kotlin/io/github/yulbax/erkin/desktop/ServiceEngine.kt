package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineListener
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.vpn.mihomo.DesktopOs
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import io.github.yulbax.erkin.vpn.mihomo.MihomoPlatform
import io.github.yulbax.erkin.vpn.mihomo.ProbeInbound
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ServiceEngine(
    private val service: DesktopService,
    private val directProcesses: List<String>,
    private val listener: EngineListener,
    private val log: AppLog
) : VpnEngine {

    override val probeSocksPort: Int = freeLoopbackPort()
    override val probeUsername: String = randomToken()
    override val probePassword: String = randomToken()
    private val byeDpiProbeSocksPort: Int = freeLoopbackPort()
    private val desktopOs = if (DesktopPaths.isWindows) DesktopOs.WINDOWS else DesktopOs.LINUX

    @Volatile private var client: ServiceClient? = null
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

    override fun byeDpiProbePort(byeDpiPort: Int): Int = byeDpiProbeSocksPort

    @Synchronized
    override fun stop() {
        terminate()
    }

    private fun launch(config: EngineConfig) {
        val connected = service.control()
        connected.start(buildConfig(config), MihomoConfigBuilder.BYEDPI_PROXY, config.network.byeDpiDns.nameserver())
        client = connected
        log.i(TAG, "mihomo started by the ERKIN service ${service.hello?.version.orEmpty()}")
        traffic = sampleTraffic()
    }

    private fun sampleTraffic(): ScheduledExecutorService {
        var last: ServiceClient.Traffic? = null
        return Executors.newSingleThreadScheduledExecutor { Thread(it, "mihomo-traffic").apply { isDaemon = true } }
            .apply {
                scheduleAtFixedRate({
                    val now = runCatching { service.running()?.traffic() }.getOrNull() ?: return@scheduleAtFixedRate
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
        engine = config,
        probe = ProbeInbound(probeSocksPort, probeUsername, probePassword),
        platform = MihomoPlatform.Desktop(desktopOs, directProcesses, SystemDnsDetector.detect(), byeDpiProbeSocksPort)
    )

    private companion object {
        const val TAG = "ServiceEngine"
    }
}
