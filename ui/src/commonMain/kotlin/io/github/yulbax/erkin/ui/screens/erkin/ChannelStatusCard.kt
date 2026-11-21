package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.CountryFlag
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.LocalDarkTheme
import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import io.github.yulbax.erkin.vpn.ExitCountry
import androidx.compose.foundation.layout.RowScope
import io.github.yulbax.erkin.ui.screens.apps.warningColor
import io.github.yulbax.erkin.ui.screens.apps.channelColor

@Composable
internal fun ChannelStatusCard(
    type: ConnectionType,
    active: Boolean,
    up: Boolean,
    hasApps: Boolean,
    shape: Shape,
    topInset: Dp,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    latencyMs: Int = 0,
    noServers: Boolean = false,
    country: ExitCountry = ExitCountry.Unknown,
    reachable: Int = 0,
    total: Int = 0,
    checking: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val byedpiBlocked = type == ConnectionType.BYEDPI && active && !up && !checking && total > 0 && reachable == 0
    val tone = when {
        noServers || !hasApps || !active -> ChannelTone.OFF
        byedpiBlocked -> ChannelTone.BLOCKED
        up -> ChannelTone.UP
        else -> ChannelTone.WAITING
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var waitedForCountry by remember(up) { mutableStateOf(false) }
    LaunchedEffect(up) {
        if (up) {
            delay(COUNTRY_WAIT)
            waitedForCountry = true
        }
    }
    Surface(
        shape = shape,
        color = if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = topInset, bottom = bottomInset)
                .heightIn(min = MIN_VISIBLE_HEIGHT)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides tone.color()) {
                ConnectionTypeIcon(type, Modifier.size(18.dp))
            }
            Text(type.label(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                when {
                    noServers -> Status(stringResource(Res.string.channel_status_no_servers), muted)
                    !hasApps -> Status(stringResource(Res.string.channel_status_no_apps), muted)
                    !active -> Status(stringResource(Res.string.channel_status_off), muted)
                    byedpiBlocked -> Status("$reachable/$total", muted)
                    !up -> WaveDots(color = muted)
                    type == ConnectionType.VPN && country == ExitCountry.Pending && !waitedForCountry -> WaveDots(color = muted)
                    type == ConnectionType.BYEDPI -> when {
                        total > 0 -> Status("$reachable/$total", muted)
                        checking -> WaveDots(color = muted)
                        else -> Status(stringResource(Res.string.channel_status_choose_lists), muted)
                    }
                    else -> {
                        Status(ProbeResult.Answered(latencyMs).label(), muted)
                        if (country is ExitCountry.Known) CountryFlag(country.code, fontSize = 18.sp, framed = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Status(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(1f, fill = false).overflowMarquee()
    )
}

private val MIN_VISIBLE_HEIGHT = 40.dp
private val COUNTRY_WAIT = 1_500.milliseconds

private enum class ChannelTone {
    OFF,
    WAITING,
    UP,
    BLOCKED;

    @Composable
    fun color(): Color = when (this) {
        OFF -> MaterialTheme.colorScheme.outline
        WAITING -> warningColor()
        UP -> channelColor(ConnectionType.VPN)
        BLOCKED -> channelColor(ConnectionType.BLOCK)
    }
}
