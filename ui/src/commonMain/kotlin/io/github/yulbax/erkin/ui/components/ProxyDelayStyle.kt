package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.server_ping_needs_connection
import io.github.yulbax.erkin.ui.res.server_ping_timeout
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProxyDelay.label(): String = when (this) {
    ProxyDelay.Failed -> stringResource(Res.string.server_ping_timeout)
    ProxyDelay.NeedsConnection -> stringResource(Res.string.server_ping_needs_connection)
    is ProxyDelay.Measured -> "$ms ms"
}

fun ProxyDelay.color(): Color = when (this) {
    ProxyDelay.Failed -> PING_RED
    ProxyDelay.NeedsConnection -> StatusAmber
    is ProxyDelay.Measured -> when {
        ms <= 100 -> StatusGreen
        ms < 450 -> lerp(StatusGreen, StatusAmber, (ms - 100) / 350f)
        ms < 900 -> lerp(StatusAmber, PING_RED, (ms - 450) / 450f)
        else -> PING_RED
    }
}

private val PING_RED = Color(0xFFE53935)
