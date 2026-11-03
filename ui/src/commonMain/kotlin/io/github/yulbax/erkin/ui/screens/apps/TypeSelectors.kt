package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.ChainIcon
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.components.ui
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.LocalDarkTheme
import io.github.yulbax.erkin.ui.viewmodel.ServerLabel
import org.jetbrains.compose.resources.stringResource

private val SERVER_ARROW_SHIFT = 16.dp
private val MENU_ORDER = listOf(ConnectionType.DIRECT, ConnectionType.BYEDPI, ConnectionType.VPN, ConnectionType.BLOCK)
private val CHANNEL_WIDTH = 112.dp
private val CHANNEL_MAX_WIDTH = 140.dp
private val CHANNEL_HEIGHT = 40.dp
private val BULK_HEIGHT = 40.dp
private val BULK_SERVER_WIDTH = 32.dp

private const val TINT_ALPHA_DARK = 0.2f
private const val TINT_ALPHA_LIGHT = 0.14f

private class ChannelAccent(val dark: Color, val light: Color)

private val ConnectionType.accent: ChannelAccent?
    get() = when (this) {
        ConnectionType.BLOCK -> ChannelAccent(dark = Color(0xFFF2554D), light = Color(0xFFC62828))
        ConnectionType.DIRECT -> null
        ConnectionType.BYEDPI -> ChannelAccent(dark = Color(0xFF1FA3CF), light = Color(0xFF0E7490))
        ConnectionType.VPN -> ChannelAccent(dark = Color(0xFF2BAE5A), light = Color(0xFF15803D))
    }

private class ChannelColors(val container: Color, val icon: Color)

@Composable
private fun ConnectionType.colors(): ChannelColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val color = accent?.let { if (dark) it.dark else it.light }
        ?: return ChannelColors(scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
    return ChannelColors(color.copy(alpha = if (dark) TINT_ALPHA_DARK else TINT_ALPHA_LIGHT), color)
}

@Composable
internal fun ChannelButton(
    type: ConnectionType,
    server: ServerLabel?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = type.colors()
    val width = if (server != null) {
        Modifier.widthIn(min = CHANNEL_WIDTH, max = CHANNEL_MAX_WIDTH)
    } else {
        Modifier.width(CHANNEL_WIDTH)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = colors.container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = width.height(CHANNEL_HEIGHT).alpha(if (enabled) 1f else DISABLED_ALPHA)
    ) {
        Row(
            modifier = Modifier.width(IntrinsicSize.Max).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.icon) {
                SourceIcon(
                    source = if (server?.isChain == true) ChainIcon else type.ui.icon,
                    contentDescription = type.label(),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = server?.name ?: type.label(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ChannelMenu(
    expanded: Boolean,
    current: ConnectionType?,
    vpnEnabled: Boolean,
    canChooseServer: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ConnectionType) -> Unit,
    onChooseServer: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp)) {
        MENU_ORDER.forEach { type ->
            if (type == ConnectionType.BLOCK) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Column {
                        Text(type.label())
                        if (type == ConnectionType.BLOCK) {
                            Text(
                                stringResource(Res.string.apps_block_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                leadingIcon = { ConnectionTypeIcon(type, Modifier.size(20.dp)) },
                trailingIcon = {
                    val serverButton = type == ConnectionType.VPN && canChooseServer
                    Row(
                        modifier = Modifier.offset(x = if (serverButton) SERVER_ARROW_SHIFT else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (type == current) Icon(Icons.Filled.Check, contentDescription = null)
                        if (serverButton) {
                            IconButton(onClick = onChooseServer) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(Res.string.apps_choose_server_cd)
                                )
                            }
                        }
                    }
                },
                enabled = vpnEnabled || !type.needsServer,
                onClick = { onSelect(type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BulkChannelBar(
    enabled: Boolean,
    blockEnabled: Boolean,
    vpnEnabled: Boolean,
    canChooseServer: Boolean,
    onSelect: (ConnectionType) -> Unit,
    onChooseServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val types = ConnectionType.entries
    val lastIndex = if (canChooseServer) types.size else types.lastIndex
    Row(
        modifier = modifier.height(BULK_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        types.forEachIndexed { index, type ->
            ElevatedToggleButton(
                checked = false,
                onCheckedChange = { onSelect(type) },
                enabled = enabled && (vpnEnabled || !type.needsServer) && (blockEnabled || type != ConnectionType.BLOCK),
                shapes = shapes(index, lastIndex),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                ConnectionTypeIcon(type, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(type.label(), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
        }
        if (canChooseServer) {
            ElevatedToggleButton(
                checked = false,
                onCheckedChange = { onChooseServer() },
                enabled = enabled && vpnEnabled,
                shapes = shapes(lastIndex, lastIndex),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(BULK_SERVER_WIDTH).fillMaxHeight()
            ) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(Res.string.apps_choose_server_cd),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapes(index: Int, lastIndex: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

private const val DISABLED_ALPHA = 0.6f
