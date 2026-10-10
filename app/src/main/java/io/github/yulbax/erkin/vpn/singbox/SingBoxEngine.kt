package io.github.yulbax.erkin.vpn.singbox

import android.content.Context
import io.github.yulbax.erkin.vpn.DefaultNetworkMonitor
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineListener
import io.github.yulbax.erkin.vpn.core.FingerprintErrorWatch
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import io.github.yulbax.erkin.vpn.core.TunAddress
import io.github.yulbax.erkin.vpn.core.TunConfig
import io.github.yulbax.erkin.vpn.core.TunPlatform
import io.github.yulbax.erkin.vpn.core.VpnEngine
import io.github.yulbax.erkin.vpn.core.freeLoopbackPort
import io.github.yulbax.erkin.vpn.core.randomToken
import io.github.yulbax.erkin.util.ErkinLog
import libbox.BoxService
import libbox.CommandClient
import libbox.CommandClientHandler
import libbox.CommandClientOptions
import libbox.CommandServer
import libbox.CommandServerHandler
import libbox.Connections
import libbox.InterfaceUpdateListener
import libbox.Libbox
import libbox.OutboundGroupIterator
import libbox.SetupOptions
import libbox.StatusMessage
import libbox.StringIterator
import libbox.SystemProxyStatus
import libbox.TunOptions
import java.io.File

class SingBoxEngine(
    appContext: Context,
    private val tunPlatform: TunPlatform,
    private val networkMonitor: DefaultNetworkMonitor,
    private val listener: EngineListener,
    private val log: ErkinLog
) : VpnEngine, ErkinPlatformInterface, CommandServerHandler {

    override val probeSocksPort: Int = freeLoopbackPort()
    override val probeUsername: String = randomToken()
    override val probePassword: String = randomToken()

    private var commandServer: CommandServer? = null
    private var boxService: BoxService? = null
    private var statusClient: CommandClient? = null
    private var groupClient: CommandClient? = null

    private val workDir = File(appContext.filesDir, "work")
    private val boxLogFile = File(workDir, "box.log")
    private val cacheFile = File(workDir, "cache.db")
    private val fingerprintErrors = FingerprintErrorWatch { boxLogFile }

    init {
        runCatching { go.Seq.setContext(appContext) }
        runCatching {
            Libbox.setup(SetupOptions().apply {
                workDir.mkdirs()
                basePath = appContext.filesDir.absolutePath
                workingPath = workDir.absolutePath
                tempPath = appContext.cacheDir.absolutePath
            })
        }.onFailure { log.e(TAG, "Libbox.setup failed", it) }
    }

    override fun start(config: EngineConfig) {
        check(commandServer == null) { "sing-box engine is already started" }
        networkMonitor.start()
        runCatching { boxLogFile.takeIf { it.exists() }?.writeText("") }
        val server = Libbox.newCommandServer(this, COMMAND_SERVER_LOG_LINES)
        commandServer = server
        try {
            server.start()
            startService(server, config)
            startCommandClient()
        } catch (t: Throwable) {
            stop()
            throw t
        }
    }

    override fun reloadRouting(config: EngineConfig) {
        val server = checkNotNull(commandServer) { "sing-box engine is not started" }
        startService(server, config)
    }

    private fun startService(server: CommandServer, config: EngineConfig) {
        val previous = boxService
        boxService = null
        runCatching { previous?.close() }
        if (cacheFile.exists() && !cacheFile.delete()) log.w(TAG, "could not reset the sing-box cache file")
        fingerprintErrors.markCoreStart()
        val service = Libbox.newService(buildConfig(config), this)
        service.start()
        boxService = service
        server.setService(service)
    }

    override fun hasFingerprintError(): Boolean = fingerprintErrors.hasError()

    override fun selectProxy(tag: String): Boolean = runCatching {
        Libbox.newStandaloneCommandClient().selectOutbound(ConfigBuilder.PROXY_GROUP_TAG, tag)
    }.isSuccess

    override fun testProxies() {
        runCatching { groupClient?.urlTest(ConfigBuilder.PROXY_GROUP_TAG) }
            .onFailure { log.w(TAG, "urlTest failed", it) }
    }

    override fun stop() {
        runCatching { statusClient?.disconnect() }
        statusClient = null
        runCatching { groupClient?.disconnect() }
        groupClient = null
        runCatching { boxService?.close() }
        boxService = null
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { networkMonitor.stop() }
            .onFailure { log.w(TAG, "failed to stop network monitor", it) }
    }

    private fun buildConfig(config: EngineConfig): String =
        ConfigBuilder.build(
            proxies = config.proxies,
            activeProxyTag = config.activeProxyTag,
            byeDpiPackages = config.byeDpiPackages,
            vpnPackages = config.vpnPackages,
            tunneledPackages = config.tunneledPackages,
            byeDpiPort = config.byeDpiSocksPort,
            probePort = probeSocksPort,
            probeUser = probeUsername,
            probePass = probePassword,
            options = config.network
        )

    private val clientHandler = object : CommandClientHandler {
        override fun writeStatus(status: StatusMessage) {
            listener.onThroughput(status.uplink, status.downlink)
        }

        override fun writeGroups(groups: OutboundGroupIterator) {
            val delays = mutableMapOf<String, ProxyDelay>()
            while (groups.hasNext()) {
                val group = groups.next()
                if (group.tag != ConfigBuilder.PROXY_GROUP_TAG) continue
                val items = group.items
                while (items.hasNext()) {
                    val item = items.next()
                    when {
                        item.urlTestTime <= 0L -> Unit
                        item.urlTestDelay <= 0 -> delays[item.tag] = ProxyDelay.Failed
                        else -> delays[item.tag] = ProxyDelay.Measured(item.urlTestDelay)
                    }
                }
            }
            listener.onProxyDelays(delays)
        }

        override fun connected() {}
        override fun disconnected(message: String) {}
        override fun clearLogs() {}
        override fun initializeClashMode(modes: StringIterator, current: String) {}
        override fun updateClashMode(mode: String) {}
        override fun writeConnections(connections: Connections) {}
        override fun writeLogs(logs: StringIterator) {}
    }

    private fun startCommandClient() {
        statusClient = connectClient(Libbox.CommandStatus)
        groupClient = connectClient(Libbox.CommandGroup)?.also { client ->
            runCatching { client.setGroupExpand(ConfigBuilder.PROXY_GROUP_TAG, true) }
        }
    }

    private fun connectClient(command: Int): CommandClient? {
        val options = CommandClientOptions().apply {
            this.command = command
            statusInterval = STATUS_INTERVAL_NANOS
        }
        val client = Libbox.newCommandClient(clientHandler, options)
        return runCatching { client.connect() }
            .map { client }
            .onFailure { log.e(TAG, "command client ($command) connect failed", it) }
            .getOrNull()
    }

    override fun openTun(options: TunOptions): Int {
        val inet4 = mutableListOf<TunAddress>()
        options.inet4Address.let { while (it.hasNext()) it.next().let { a -> inet4.add(TunAddress(a.address(), a.prefix())) } }
        val inet6 = mutableListOf<TunAddress>()
        options.inet6Address.let { while (it.hasNext()) it.next().let { a -> inet6.add(TunAddress(a.address(), a.prefix())) } }
        val autoRoute = options.autoRoute
        val dnsServers = mutableListOf<String>()
        if (autoRoute) {
            runCatching { options.dnsServerAddress.value }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { dnsServers.add(it) }
        }
        val includePkgs = mutableListOf<String>()
        val excludePkgs = mutableListOf<String>()
        if (autoRoute) {
            options.includePackage.let { while (it.hasNext()) includePkgs.add(it.next()) }
            options.excludePackage.let { while (it.hasNext()) excludePkgs.add(it.next()) }
        }
        return tunPlatform.openTun(
            TunConfig(
                mtu = options.mtu,
                inet4 = inet4,
                inet6 = inet6,
                autoRoute = autoRoute,
                dnsServers = dnsServers,
                includePackages = includePkgs,
                excludePackages = excludePkgs
            )
        )
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        check(tunPlatform.protectFd(fd)) { "failed to protect socket fd=$fd" }
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int = tunPlatform.findConnectionOwner(
        ipProtocol, sourceAddress, sourcePort, destinationAddress, destinationPort
    )

    override fun packageNameByUid(uid: Int): String = tunPlatform.packageNameForUid(uid)

    override fun uidByPackageName(packageName: String): Int = tunPlatform.uidForPackageName(packageName)

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkMonitor.setListener { name, index ->
            listener.updateDefaultInterface(name, index, false, false)
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkMonitor.setListener(null)
    }

    override fun postServiceClose() {
        listener.onStopRequested()
    }

    override fun serviceReload() {}
    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus()
    override fun setSystemProxyEnabled(enabled: Boolean) {}

    private companion object {
        const val TAG = "SingBoxEngine"
        const val COMMAND_SERVER_LOG_LINES = 300
        const val STATUS_INTERVAL_NANOS = 1_000_000_000L
    }
}
