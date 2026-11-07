package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import io.github.yulbax.erkin.ui.components.AppDropdownMenu
import io.github.yulbax.erkin.ui.components.ChainIcon
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.IconSource
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.components.ui
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.platform.appsCanBypass
import io.github.yulbax.erkin.ui.platform.platformChannels
import org.jetbrains.compose.resources.StringResource
import io.github.yulbax.erkin.ui.theme.LocalDarkTheme
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.ui.viewmodel.ServerLabel
import org.jetbrains.compose.resources.stringResource

internal class ChannelChoice(val type: ConnectionType, val note: StringResource? = null)

private val BLOCK_CHOICE = ChannelChoice(ConnectionType.BLOCK, Res.string.apps_block_note)

internal val appChannelChoices: List<ChannelChoice> = if (appsCanBypass) {
    listOf(
        ChannelChoice(ConnectionType.BYPASS, Res.string.apps_bypass_note),
        ChannelChoice(ConnectionType.DIRECT, Res.string.apps_direct_note),
        ChannelChoice(ConnectionType.BYEDPI),
        ChannelChoice(ConnectionType.VPN),
        BLOCK_CHOICE
    )
} else {
    listOf(ChannelChoice(ConnectionType.DIRECT), ChannelChoice(ConnectionType.BYEDPI), ChannelChoice(ConnectionType.VPN), BLOCK_CHOICE)
}

internal val ruleChannelChoices: List<ChannelChoice> =
    listOf(ChannelChoice(ConnectionType.DIRECT), ChannelChoice(ConnectionType.BYEDPI), ChannelChoice(ConnectionType.VPN), BLOCK_CHOICE)

private val CHANNEL_WIDTH = 112.dp
private val CHANNEL_MAX_WIDTH = 140.dp
private val CHANNEL_HEIGHT = 40.dp
private val BULK_HEIGHT = 40.dp
private val BULK_SERVER_WIDTH = 32.dp
private val BULK_SPACING = 2.dp
private val BULK_LABELED_MIN_WIDTH = 64.dp

private const val TINT_ALPHA_DARK = 0.2f
private const val TINT_ALPHA_LIGHT = 0.14f

private class ChannelAccent(val dark: Color, val light: Color)

private val ConnectionType.accent: ChannelAccent?
    get() = when (this) {
        ConnectionType.BLOCK -> ChannelAccent(dark = Color(0xFFF2554D), light = Color(0xFFC62828))
        ConnectionType.BYPASS, ConnectionType.DIRECT -> null
        ConnectionType.BYEDPI -> ChannelAccent(dark = Color(0xFF1FA3CF), light = Color(0xFF0E7490))
        ConnectionType.VPN -> ChannelAccent(dark = Color(0xFF2BAE5A), light = Color(0xFF15803D))
    }

private val WARNING = ChannelAccent(dark = Color(0xFFF9A825), light = Color(0xFFB26A00))
private val WARNING_BORDER = 1.5.dp

internal fun ServerLabel?.icon(type: ConnectionType): IconSource = when {
    this?.unreachable == true -> IconSource.Vector(Icons.Filled.Warning)
    this?.isChain == true -> ChainIcon
    else -> type.ui.icon
}

@Composable
internal fun channelColor(type: ConnectionType): Color = type.colors().icon

@Composable
internal fun warningColor(): Color = if (LocalDarkTheme.current) WARNING.dark else WARNING.light

private class ChannelColors(val container: Color, val icon: Color)

@Composable
private fun ConnectionType.colors(): ChannelColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val color = accent?.let { if (dark) it.dark else it.light }
        ?: return ChannelColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
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
    val warning = warningColor().takeIf { server?.unreachable == true }
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
        border = warning?.let { BorderStroke(WARNING_BORDER, it) },
        modifier = width.height(CHANNEL_HEIGHT).alpha(if (enabled) 1f else DISABLED_ALPHA)
    ) {
        Row(
            modifier = Modifier.width(IntrinsicSize.Max).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides (warning ?: colors.icon)) {
                SourceIcon(
                    source = server.icon(type),
                    contentDescription = if (warning != null) stringResource(Res.string.apps_server_unreachable_cd) else type.label(),
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
    channels: List<ChannelChoice>,
    expanded: Boolean,
    current: ConnectionType?,
    vpnEnabled: Boolean,
    server: ServerLabel?,
    onDismiss: () -> Unit,
    onSelect: (ConnectionType) -> Unit,
    onChooseServer: () -> Unit
) {
    AppDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        channels.forEach { choice ->
            val type = choice.type
            if (type == ConnectionType.BLOCK) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Column {
                        Text(type.label())
                        if (choice.note != null) {
                            Text(
                                stringResource(choice.note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                leadingIcon = { ConnectionTypeIcon(type, Modifier.size(20.dp)) },
                trailingIcon = if (type == current) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
                enabled = vpnEnabled || !type.needsServer,
                onClick = { onSelect(type) }
            )
            if (type == ConnectionType.VPN && server != null) ServerMenuItem(server, onChooseServer)
        }
    }
}

@Composable
private fun ServerMenuItem(server: ServerLabel, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { ServerChoiceText(server) },
        leadingIcon = {
            if (server.unreachable) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = warningColor(), modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.size(20.dp))
            }
        },
        trailingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.apps_choose_server_cd),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick
    )
}

@Composable
internal fun ServerChoiceText(server: ServerLabel, modifier: Modifier = Modifier) {
    val warning = warningColor().takeIf { server.unreachable }
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.apps_server_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            server.name,
            style = MaterialTheme.typography.bodySmall,
            color = warning ?: MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (warning != null) {
            Text(
                stringResource(Res.string.apps_server_unreachable),
                style = MaterialTheme.typography.labelSmall,
                color = warning
            )
        }
    }
}

@Composable
internal fun ServerChoices.menuServer(channel: ConnectionType, serverId: Long?): ServerLabel? {
    if (!canChoose) return null
    return label(channel, serverId) ?: main?.let { ServerLabel(stringResource(Res.string.app_server_main, it.name), isChain = false) }
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
    val types = platformChannels
    val lastIndex = if (canChooseServer) types.size else types.lastIndex
    BoxWithConstraints(modifier) {
        val serverWidth = if (canChooseServer) BULK_SERVER_WIDTH + BULK_SPACING else 0.dp
        val labeled = (maxWidth - serverWidth - BULK_SPACING * (types.size - 1)) / types.size >= BULK_LABELED_MIN_WIDTH
        BulkButtons(types, lastIndex, labeled, enabled, blockEnabled, vpnEnabled, canChooseServer, onSelect, onChooseServer)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BulkButtons(
    types: List<ConnectionType>,
    lastIndex: Int,
    labeled: Boolean,
    enabled: Boolean,
    blockEnabled: Boolean,
    vpnEnabled: Boolean,
    canChooseServer: Boolean,
    onSelect: (ConnectionType) -> Unit,
    onChooseServer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(BULK_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(BULK_SPACING)
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
                ConnectionTypeIcon(type, Modifier.size(if (labeled) 16.dp else 20.dp))
                if (labeled) {
                    Spacer(Modifier.width(4.dp))
                    Text(type.label(), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                }
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
