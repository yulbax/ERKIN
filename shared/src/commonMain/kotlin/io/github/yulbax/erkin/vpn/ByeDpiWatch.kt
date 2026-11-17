package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlin.time.Duration

internal class ByeDpiWatch(
    private val port: Int,
    private val params: HealthParams,
    private val snapshot: MutableStateFlow<HealthSnapshot>,
    private val channels: ChannelProbe,
    private val log: AppLog,
    private val checkInterval: Duration
) {
    suspend fun run() {
        while (true) {
            val up = channels.byeDpiReachable(port)
            val before = snapshot.getAndUpdate { it.copy(byedpiUp = up, byedpiChecked = true) }
            if (!before.byedpiChecked || before.byedpiUp != up) {
                if (up) log.i(HEALTH_TAG, "byedpi channel up") else log.w(HEALTH_TAG, "byedpi channel down")
                params.onByedpiChanged()
            }
            delay(checkInterval)
        }
    }
}
