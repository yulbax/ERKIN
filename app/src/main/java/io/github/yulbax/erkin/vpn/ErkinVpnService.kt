package io.github.yulbax.erkin.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.yulbax.erkin.BuildConfig
import io.github.yulbax.erkin.util.ErkinLog
import io.github.yulbax.erkin.vpn.core.TunConfig
import io.github.yulbax.erkin.vpn.core.TunPlatform
import io.github.yulbax.erkin.vpn.singbox.SingBoxEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.InetSocketAddress

@SuppressLint("VpnServicePolicy")
class ErkinVpnService :
    VpnService(),
    TunPlatform,
    VpnHost,
    KoinComponent {

    private val store: VpnSessionStore by inject()
    private val vpnStateRepository: VpnStateRepository by inject()
    private val commandBus: VpnCommandBus by inject()
    private val networkMonitor: DefaultNetworkMonitor by inject()
    private val erkinLog: ErkinLog by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var controller: VpnController
    private lateinit var tunSession: TunSession
    private lateinit var notification: VpnNotificationController
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        tunSession = TunSession(this, networkMonitor, erkinLog)
        notification = VpnNotificationController(this, vpnStateRepository.stats)
        controller = VpnController(scope, commandBus, vpnStateRepository, host = this, log = erkinLog) { controller ->
            VpnSession(
                engine = SingBoxEngine(applicationContext, this, networkMonitor, controller, erkinLog),
                byeDpi = ByeDpiRunner(packageName, erkinLog) { fd -> protect(fd) },
                store = store,
                stateRepository = vpnStateRepository,
                health = HealthMonitor(
                    vpnStateRepository,
                    erkinLog,
                    SocksHealthProbe(BuildConfig.VPN_HEALTH_PROBE_URL, BuildConfig.BYEDPI_HEALTH_PROBE_URL)
                ),
                quality = SocksSiteProbe,
                log = erkinLog,
                ownPackage = packageName,
                scope = scope,
                requestWork = controller::requestWork,
                onByeDpiExit = controller::onByeDpiExit
            )
        }
        networkMonitor.onUnderlyingNetworkChanged = { network ->
            runCatching { setUnderlyingNetworks(network?.let { arrayOf(it) }) }
        }
        controller.launch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop(startId, force = false)
        } else {
            notification.startForeground()
            controller.start(startId, systemInitiated = intent?.action != ACTION_START)
        }
        return START_NOT_STICKY
    }

    override fun onSessionStarting() {
        notification.startForeground()
    }

    override fun onSessionStarted() {
        notificationJob = notification.observe(scope)
    }

    override fun allowsUserStop(): Boolean = !isAlwaysOn

    override fun onSessionStopped(stopToken: Int?, stopHost: Boolean) {
        runCatching { notificationJob?.cancel() }
        notificationJob = null
        runCatching { tunSession.close() }
            .onFailure { erkinLog.w(TAG, "failed to close TUN", it) }
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }.onFailure { erkinLog.w(TAG, "failed to remove foreground notification", it) }
        if (stopHost) {
            runCatching {
                if (stopToken != null) stopSelfResult(stopToken) else stopSelf()
            }.onFailure { erkinLog.w(TAG, "failed to stop VPN service", it) }
        }
    }

    override fun openTun(config: TunConfig): Int = tunSession.open(config)

    override fun protectFd(fd: Int): Boolean = protect(fd)

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): Int = tunSession.findConnectionOwner(
        ipProtocol,
        InetSocketAddress(sourceAddress, sourcePort),
        InetSocketAddress(destinationAddress, destinationPort)
    )

    override fun packageNameForUid(uid: Int): String = tunSession.packageNameForUid(uid)

    override fun uidForPackageName(packageName: String): Int = tunSession.uidForPackageName(packageName)

    override fun onRevoke() {
        controller.stop(token = null, force = true)
    }

    override fun onDestroy() {
        networkMonitor.onUnderlyingNetworkChanged = null
        controller.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "io.github.yulbax.erkin.action.START"
        const val ACTION_STOP = "io.github.yulbax.erkin.action.STOP"

        private const val TAG = "ErkinVpnService"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ErkinVpnService::class.java).setAction(ACTION_START)
            )
        }
    }
}
