package io.github.yulbax.erkin.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.yulbax.erkin.MainActivity
import io.github.yulbax.erkin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ErkinTileService : TileService(), KoinComponent {
    private val states: VpnStateRepository by inject()
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
            launch { states.state.collect(::render) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when {
            states.state.value.isOn -> startService(Intent(this, ErkinVpnService::class.java).setAction(ErkinVpnService.ACTION_STOP))
            VpnService.prepare(this) != null -> openApp()
            else -> ErkinVpnService.start(this)
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.app_name)
        tile.state = if (state.isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(state.tileSubtitle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startLegacyActivityAndCollapse(intent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startLegacyActivityAndCollapse(intent: Intent) = startActivityAndCollapse(intent)

    private val VpnState.isOn: Boolean get() = this is VpnState.Connecting || isActive

    private val VpnState.tileSubtitle: Int
        get() = when (this) {
            VpnState.Connecting, VpnState.Verifying -> R.string.tile_connecting
            is VpnState.Connected, is VpnState.ChannelDown -> R.string.tile_connected
            VpnState.Disconnected, is VpnState.Error -> R.string.tile_off
        }
}
