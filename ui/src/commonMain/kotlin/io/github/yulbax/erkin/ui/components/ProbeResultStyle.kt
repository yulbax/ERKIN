package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.server_ping_needs_connection
import io.github.yulbax.erkin.ui.res.server_ping_login_rejected
import io.github.yulbax.erkin.ui.res.server_ping_timeout
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProbeResult.label(): String = when (this) {
    ProbeResult.NoAnswer -> stringResource(Res.string.server_ping_timeout)
    ProbeResult.NeedsConnection -> stringResource(Res.string.server_ping_needs_connection)
    ProbeResult.LoginRejected -> stringResource(Res.string.server_ping_login_rejected)
    is ProbeResult.Answered -> "$ms ms"
}

fun ProbeResult.color(): Color = when (this) {
    ProbeResult.NoAnswer, ProbeResult.LoginRejected -> PING_RED
    ProbeResult.NeedsConnection -> StatusAmber
    is ProbeResult.Answered -> when {
        ms <= 100 -> StatusGreen
        ms < 450 -> lerp(StatusGreen, StatusAmber, (ms - 100) / 350f)
        ms < 900 -> lerp(StatusAmber, PING_RED, (ms - 450) / 450f)
        else -> PING_RED
    }
}

private val PING_RED = Color(0xFFE53935)
