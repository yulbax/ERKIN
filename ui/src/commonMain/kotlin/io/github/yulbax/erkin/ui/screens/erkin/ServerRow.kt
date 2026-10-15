package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun ServerRow(
    profile: ProfileEntity,
    isSelected: Boolean,
    delay: ProxyDelay? = null,
    testing: Boolean = false,
    isBest: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val splitColors = ButtonColors(
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        disabledContentColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.onSurface
    )

    val pingColor = delay?.let { pingColor(it) }
    val rowColor = when {
        isBest && pingColor != null -> pingColor.copy(alpha = 0.14f)
        isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        color = rowColor,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile.subscriptionUrl.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = stringResource(Res.string.subscription),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        profile.name.ifEmpty { stringResource(Res.string.unnamed_profile) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (testing) {
                        Text(
                            " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WaveDots(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (delay != null) {
                        Text(
                            " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (delay) {
                                ProxyDelay.Failed -> stringResource(Res.string.server_ping_timeout)
                                is ProxyDelay.Measured -> "${delay.ms} ms"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onEdit,
                        colors = splitColors
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                            contentDescription = stringResource(Res.string.edit),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.edit))
                    }
                },
                trailingButton = {
                    Box {
                        SplitButtonDefaults.TrailingButton(
                            checked = menuExpanded,
                            onCheckedChange = { menuExpanded = it },
                            colors = splitColors
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(Res.string.more_options_cd)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.share)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    onShare()
                                    menuExpanded = false
                                }
                            )
                            if (profile.subscriptionUrl.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.refresh_subscription)) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        onRefresh()
                                        menuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    onDelete()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

private val PING_RED = Color(0xFFE53935)

@Composable
private fun WaveDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pingWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing)),
        label = "pingWavePhase"
    )
    val lift = with(LocalDensity.current) { WAVE_LIFT.toPx() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(16.dp)
    ) {
        repeat(WAVE_DOTS) { index ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = -lift * sin(2 * PI.toFloat() * (phase - index * WAVE_DOT_SHIFT)).coerceAtLeast(0f)
                    }
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private const val WAVE_DOTS = 3
private const val WAVE_PERIOD_MS = 1_000
private const val WAVE_DOT_SHIFT = 0.15f
private val WAVE_LIFT = 4.dp

private fun pingColor(delay: ProxyDelay): Color = when (delay) {
    ProxyDelay.Failed -> PING_RED
    is ProxyDelay.Measured -> when {
        delay.ms <= 100 -> StatusGreen
        delay.ms < 450 -> lerp(StatusGreen, StatusAmber, (delay.ms - 100) / 350f)
        delay.ms < 900 -> lerp(StatusAmber, PING_RED, (delay.ms - 450) / 450f)
        else -> PING_RED
    }
}
