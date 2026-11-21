package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.text
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.LocalDarkTheme
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.screens.apps.channelColor
import io.github.yulbax.erkin.ui.screens.apps.channelTint
import io.github.yulbax.erkin.ui.screens.apps.warningColor
import androidx.compose.ui.graphics.compositeOver
import io.github.yulbax.erkin.util.formatRate
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.HealthChannel
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
    val busy = state.isBusy
    val tone = when {
        state is VpnState.Error -> CardTone.ERROR
        busy || state is VpnState.ChannelDown -> CardTone.BUSY
        connected -> CardTone.ON
        else -> CardTone.OFF
    }
    val colors = tone.colors()
    val container by animateColorAsState(colors.container, tween(500), label = "container")
    val content by animateColorAsState(colors.content, tween(500), label = "content")
    val button by animateColorAsState(colors.button, tween(500), label = "button")
    val onButton by animateColorAsState(colors.onButton, tween(500), label = "onButton")

    val title = when (state) {
        VpnState.Disconnected -> stringResource(Res.string.vpn_disconnected)
        VpnState.Connecting -> stringResource(Res.string.vpn_connecting)
        VpnState.Verifying -> stringResource(Res.string.vpn_verifying)
        is VpnState.Connected -> stringResource(Res.string.vpn_connected)
        is VpnState.ChannelDown -> stringResource(Res.string.vpn_server_down)
        is VpnState.Error -> stringResource(Res.string.vpn_not_connected)
    }
    val subtitle = when (state) {
        VpnState.Disconnected -> stringResource(Res.string.vpn_tap_to_connect)
        VpnState.Connecting -> stringResource(Res.string.vpn_establishing_tunnel)
        VpnState.Verifying -> stringResource(Res.string.vpn_checking_connectivity)
        is VpnState.Connected -> stringResource(Res.string.vpn_tap_to_disconnect)
        is VpnState.ChannelDown -> when (state.channel) {
            HealthChannel.VPN -> stringResource(Res.string.vpn_server_down_hint)
            HealthChannel.BYEDPI -> stringResource(Res.string.byedpi_down_hint)
        }
        is VpnState.Error -> state.failure.text()
    }
    val shape = RoundedCornerShape(CONNECTION_CARD_CORNER)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(CARD_SHADOW, shape)
            .clip(shape)
            .background(container)
            .pointerInput(Unit) {}
            .padding(horizontal = 24.dp, vertical = 20.dp),
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
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
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
            PowerToggle(
                busy = busy,
                raised = tone == CardTone.OFF,
                enabled = enabled,
                color = button,
                iconColor = onButton,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun PowerToggle(
    busy: Boolean,
    raised: Boolean,
    enabled: Boolean,
    color: Color,
    iconColor: Color,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = CircleShape,
        color = color,
        shadowElevation = if (raised) 4.dp else 0.dp,
        modifier = Modifier
            .size(64.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (busy) {
                CircularProgressIndicator(
                    color = iconColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(Res.string.toggle_connection_cd),
                    tint = iconColor,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

internal val CONNECTION_CARD_CORNER = 28.dp
private val CARD_SHADOW = 6.dp

private class ToneColors(val container: Color, val content: Color, val button: Color, val onButton: Color)

private enum class CardTone {
    OFF,
    BUSY,
    ON,
    ERROR;

    @Composable
    fun colors(): ToneColors {
        val scheme = MaterialTheme.colorScheme
        val accent = when (this) {
            OFF -> return ToneColors(
                if (LocalDarkTheme.current) scheme.surfaceContainerHigh else scheme.surfaceContainerHighest,
                scheme.onSurfaceVariant,
                Color.White,
                Color(0xFF1C1B1F)
            )
            BUSY -> warningColor()
            ON -> channelColor(ConnectionType.VPN)
            ERROR -> channelColor(ConnectionType.BLOCK)
        }
        val onAccent = if (LocalDarkTheme.current) Color(0xFF1C1B1F) else Color.White
        return ToneColors(channelTint(accent).compositeOver(scheme.surface), scheme.onSurface, accent, onAccent)
    }
}
