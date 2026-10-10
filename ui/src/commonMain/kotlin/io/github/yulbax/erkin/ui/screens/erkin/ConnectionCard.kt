package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.StatusOrange
import io.github.yulbax.erkin.util.formatRate
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.VpnState

@Composable
internal fun ConnectionCard(
    state: VpnState,
    stats: ConnectionStats,
    connected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val busy = state.isBusy
    val isError = state is VpnState.Error
    val vivid = isError || connected || busy

    val targetTop = when {
        isError -> Color(0xFFB3261E)
        busy -> StatusOrange
        connected -> Color(0xFF2E7D32)
        else -> scheme.surfaceVariant
    }
    val targetBottom = when {
        isError -> Color(0xFF8C1D18)
        busy -> Color(0xFFF57F17)
        connected -> Color(0xFF1B5E20)
        else -> scheme.surface
    }
    val top by animateColorAsState(targetTop, tween(500), label = "top")
    val bottom by animateColorAsState(targetBottom, tween(500), label = "bottom")
    val content by animateColorAsState(
        when {
            busy -> Color(0xFF211A00)
            vivid -> Color.White
            else -> scheme.onSurfaceVariant
        },
        tween(500),
        label = "content"
    )

    val title = when (state) {
        VpnState.Disconnected -> stringResource(Res.string.vpn_disconnected)
        VpnState.Connecting -> stringResource(Res.string.vpn_connecting)
        VpnState.Verifying -> stringResource(Res.string.vpn_verifying)
        is VpnState.Connected -> stringResource(Res.string.vpn_connected)
        is VpnState.Error -> stringResource(Res.string.vpn_not_connected)
    }
    val subtitle = when (state) {
        VpnState.Disconnected -> stringResource(Res.string.vpn_tap_to_connect)
        VpnState.Connecting -> stringResource(Res.string.vpn_establishing_tunnel)
        VpnState.Verifying -> stringResource(Res.string.vpn_checking_connectivity)
        is VpnState.Connected -> stringResource(Res.string.vpn_tap_to_disconnect)
        is VpnState.Error -> if (state.isProcessLookupUnavailable) stringResource(Res.string.error_process_lookup) else state.message
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(horizontal = 24.dp, vertical = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val showSpeed = connected && (stats.vpnUp || stats.byedpiUp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (showSpeed) {
                        "↓ ${formatRate(stats.downlink)}   ↑ ${formatRate(stats.uplink)}"
                    } else " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = if (showSpeed) 0.85f else 0f)
                )
            }

            Spacer(Modifier.width(16.dp))
            PowerToggle(busy = busy, vivid = vivid, enabled = enabled, content = content, onToggle = onToggle)
        }
    }
}

@Composable
private fun PowerToggle(
    busy: Boolean,
    vivid: Boolean,
    enabled: Boolean,
    content: Color,
    onToggle: () -> Unit
) {
    val circleColor = if (vivid) Color.White.copy(alpha = 0.2f) else Color.White
    val iconColor = if (vivid) content else Color(0xFF1C1B1F)
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = CircleShape,
        color = circleColor,
        shadowElevation = if (vivid) 0.dp else 4.dp,
        modifier = Modifier
            .size(80.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (busy) {
                CircularProgressIndicator(
                    color = iconColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(Res.string.toggle_connection_cd),
                    tint = iconColor,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}
