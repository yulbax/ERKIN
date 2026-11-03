package io.github.yulbax.erkin.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.yulbax.erkin.MainActivity
import io.github.yulbax.erkin.R
import io.github.yulbax.erkin.util.createNotificationChannel
import io.github.yulbax.erkin.util.formatRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


class VpnNotificationController(
    private val service: Service,
    private val stats: StateFlow<ConnectionStats>
) {
    fun startForeground() {
        service.createNotificationChannel(
            CHANNEL_ID,
            service.getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(service, NOTIFICATION_ID, build(), type)
    }

    
    @OptIn(FlowPreview::class)
    fun observe(scope: CoroutineScope): Job = scope.launch {
        stats
            .map { Triple(statusLine(it), speedLine(it), it.configName) }
            .distinctUntilChanged()
            .debounce(1000.milliseconds)
            .collect {
                runCatching {
                    service.getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, build())
                }
            }
    }

    private fun build(): Notification {
        val contentIntent = PendingIntent.getActivity(
            service, 0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            service, 1,
            Intent(service, ErkinVpnService::class.java).setAction(ErkinVpnService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val current = stats.value
        val title = current.configName.ifBlank { service.getString(R.string.app_name) }
        val status = statusLine(current)
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(status)
            .setSubText(speedLine(current))
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, service.getString(R.string.vpn_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun statusLine(stats: ConnectionStats): String {
        val vpn = service.getString(
            if (stats.vpnUp) R.string.notification_vpn_up else R.string.notification_vpn_down
        )
        val byedpi = service.getString(
            when {
                !stats.byedpiActive -> R.string.notification_byedpi_off
                stats.byedpiUp -> R.string.notification_byedpi_up
                else -> R.string.notification_byedpi_down
            }
        )
        return "$vpn | $byedpi"
    }

    private fun speedLine(stats: ConnectionStats): String =
        if (stats.vpnUp || stats.byedpiUp) {
            service.getString(R.string.notification_speed, formatRate(stats.downlink), formatRate(stats.uplink)).trim()
        } else {
            ""
        }

    companion object {
        private const val CHANNEL_ID = "erkin_vpn"
        private const val NOTIFICATION_ID = 1
    }
}
