package io.github.yulbax.erkin.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.util.ErkinLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val database: AppDatabase by inject()
    private val log: ErkinLog by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS.milliseconds) {
                    val settings = database.settingsDao().observeSettings().first() ?: SettingsEntity()
                    val serverSelected = database.profileDao().getSelected() != null
                    val hasByeDpiApps = database.appDao().getAllAppsSnapshot().any { it.connectionType == ConnectionType.BYEDPI }
                    val consentGranted = VpnService.prepare(context) == null
                    if (settings.autoConnect && (serverSelected || hasByeDpiApps) && consentGranted) {
                        log.i(TAG, "boot received: starting VPN")
                        ErkinVpnService.start(context)
                    } else {
                        log.i(
                            TAG,
                            "autostart skipped: autoConnect=${settings.autoConnect} " +
                                "serverSelected=$serverSelected byedpiApps=$hasByeDpiApps consent=$consentGranted"
                        )
                    }
                }
            } catch (error: CancellationException) {
                log.w(TAG, "autostart timed out", error)
            } catch (error: Exception) {
                log.e(TAG, "autostart failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Autostart"
        const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
