package io.github.yulbax.erkin.vpn.mihomo

import android.content.Context
import io.github.yulbax.erkin.util.ErkinLog
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineListener
import io.github.yulbax.erkin.vpn.core.TunAddress
import io.github.yulbax.erkin.vpn.core.TunConfig
import io.github.yulbax.erkin.vpn.core.TunPlatform
import io.github.yulbax.erkin.vpn.core.VPN_DELAY_TIMEOUT_MS
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihomocore.Mihomocore
import mihomocore.Platform

class MihomoEngine(
    private val appContext: Context,
    private val tunPlatform: TunPlatform,
    private val listener: EngineListener,
    private val log: ErkinLog
) : VpnEngine {

    override val probeSocksPort: Int = freeLoopbackPort()
    override val probeUsername: String = randomToken()
    override val probePassword: String = randomToken()

    @Volatile private var activeProxyTag: String = ""
    private var tunConfig: TunConfig? = null
    private var traffic: ScheduledExecutorService? = null

    private val platform = object : Platform {
        override fun protect(fd: Int): Boolean = tunPlatform.protectFd(fd)

        override fun packageName(protocol: Int, source: String, target: String): String = runCatching {
            val (sourceHost, sourcePort) = splitAddress(source)
            val (targetHost, targetPort) = splitAddress(target)
            val uid = tunPlatform.findConnectionOwner(protocol, sourceHost, sourcePort, targetHost, targetPort)
            tunPlatform.packageNameForUid(uid)
        }.getOrDefault("")
    }

    @Synchronized
    override fun start(config: EngineConfig) {
        check(traffic == null) { "mihomo engine is already started" }
        MihomoRuntime.ensureSetup(appContext, log)
        launch(config)
        traffic = sampleTraffic()
    }

    @Synchronized
    override fun reloadRouting(config: EngineConfig) {
        val tun = tunConfigOf(config)
        val current = tunConfig
        if (tun == current) {
            reload(config)
            return
        }
        val fd = try {
            tunPlatform.openTun(tun)
        } catch (error: Exception) {
            if (current == null) throw error
            log.w(TAG, "could not recreate the tun; keeping the current one", error)
            reload(config)
            return
        }
        startCore(config, tun, fd)
    }

    @Synchronized
    override fun stop() {
        traffic?.shutdownNow()
        traffic = null
        tunConfig = null
        runCatching { Mihomocore.stop() }.onFailure { log.w(TAG, "mihomo stop failed", it) }
    }

    override suspend fun measureVpnDelay(): Int? {
        val tag = activeProxyTag.takeIf { it.isNotEmpty() } ?: return null
        return withContext(Dispatchers.IO) {
            Mihomocore.urlTest(tag, ProbeUrls.VPN, VPN_DELAY_TIMEOUT_MS).takeIf { it > 0 }
        }
    }

    private fun launch(config: EngineConfig) {
        val tun = tunConfigOf(config)
        startCore(config, tun, tunPlatform.openTun(tun))
    }

    private fun reload(config: EngineConfig) {
        activeProxyTag = config.activeProxyTag
        log.i(TAG, "reloading the core on the current tun")
        Mihomocore.reload(buildConfig(config), MihomoConfigBuilder.BYEDPI_PROXY, config.network.dnsRemote)
    }

    private fun tunConfigOf(config: EngineConfig): TunConfig {
        val ipv6 = config.network.ipv6Mode.ipv6Enabled
        return TunConfig(
            mtu = config.network.mtu,
            inet4 = listOf(TunAddress(TUN_INET4, TUN_INET4_PREFIX)),
            inet6 = if (ipv6) listOf(TunAddress(TUN_INET6, TUN_INET6_PREFIX)) else emptyList(),
            autoRoute = true,
            dnsServers = listOf(TUN_DNS),
            includePackages = config.tunneledPackages.sorted(),
            excludePackages = emptyList()
        )
    }

    private fun startCore(config: EngineConfig, tun: TunConfig, fd: Int) {
        activeProxyTag = config.activeProxyTag
        log.i(TAG, "system dns: ${MihomoRuntime.describeSystemDns()}")
        val ipv6 = tun.inet6.isNotEmpty()
        val gateway = buildList {
            add("$TUN_INET4/$TUN_INET4_PREFIX")
            if (ipv6) add("$TUN_INET6/$TUN_INET6_PREFIX")
        }.joinToString(",")
        Mihomocore.start(
            buildConfig(config),
            fd,
            config.network.mtu,
            gateway,
            DNS_HIJACK,
            MihomoConfigBuilder.BYEDPI_PROXY,
            config.network.dnsRemote,
            platform
        )
        tunConfig = tun
    }

    private fun buildConfig(config: EngineConfig): String = MihomoConfigBuilder.build(
        engine = config,
        probe = ProbeInbound(probeSocksPort, probeUsername, probePassword),
        platform = MihomoPlatform.Android(MihomoRuntime.systemDns())
    )

    private fun sampleTraffic(): ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "mihomo-traffic").apply { isDaemon = true } }.apply {
            scheduleAtFixedRate({
                runCatching { Mihomocore.currentTraffic() }.getOrNull()?.let { listener.onThroughput(it.up, it.down) }
            }, 1, 1, TimeUnit.SECONDS)
        }

    private fun splitAddress(address: String): Pair<String, Int> {
        val host = address.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        return host to address.substringAfterLast(':').toInt()
    }

    private companion object {
        const val TAG = "MihomoEngine"
        const val TUN_INET4 = "172.19.0.1"
        const val TUN_INET4_PREFIX = 30
        const val TUN_INET6 = "fdfe:dcba:9876::1"
        const val TUN_INET6_PREFIX = 126
        const val TUN_DNS = "172.19.0.2"
        const val DNS_HIJACK = "0.0.0.0,::"
    }
}
