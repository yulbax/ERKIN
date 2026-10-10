package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.label

private val TYPE_ORDER = listOf(ConnectionType.DIRECT, ConnectionType.BYEDPI, ConnectionType.VPN)
private val SELECTOR_HEIGHT = 48.dp
private val APP_SELECTOR_HEIGHT = 48.dp
private val APP_SELECTOR_WIDTH = 150.dp
private val SELECTOR_ICON_WIDTH = 48.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GlobalTypeConnectedGroup(
    vpnEnabled: Boolean,
    onSelect: (ConnectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(SELECTOR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TYPE_ORDER.forEachIndexed { index, type ->
            ElevatedToggleButton(
                checked = false,
                onCheckedChange = { onSelect(type) },
                enabled = vpnEnabled || type != ConnectionType.VPN,
                shapes = shapes(index),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
            ) {
                ConnectionTypeIcon(type, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = type.label(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppRowConnectedGroup(
    selected: ConnectionType?,
    vpnEnabled: Boolean,
    onSelect: (ConnectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(APP_SELECTOR_HEIGHT)
            .width(APP_SELECTOR_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TYPE_ORDER.forEachIndexed { index, type ->
            val isSelected = type == selected

            ToggleButton(
                checked = isSelected,
                onCheckedChange = { if (it) onSelect(type) },
                enabled = vpnEnabled || type != ConnectionType.VPN,
                shapes = shapes(index),
                modifier = Modifier
                    .then(if (isSelected) Modifier.weight(1f) else Modifier.width(SELECTOR_ICON_WIDTH))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    ConnectionTypeIcon(type, Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapes(index: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    TYPE_ORDER.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}
