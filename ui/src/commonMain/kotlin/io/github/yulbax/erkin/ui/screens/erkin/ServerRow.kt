package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal class ServerActions(
    val onEdit: () -> Unit,
    val onShare: (() -> Unit)?,
    val onRefreshSubscription: (() -> Unit)?,
    val onDelete: () -> Unit
)

@Composable
internal fun ServerRow(
    profile: ProfileEntity,
    isSelected: Boolean,
    actions: ServerActions,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    delay: ProxyDelay? = null,
    testing: Boolean = false,
    isBest: Boolean = false,
    chainLabel: String? = null,
    fromSubscription: Boolean = false,
    selecting: Boolean = false,
    checked: Boolean = false,
    handle: (@Composable () -> Unit)? = null
) {
    ServerRowLayout(
        title = profile.name.ifEmpty { stringResource(Res.string.unnamed_profile) },
        subtitle = chainLabel ?: profile.type,
        isSelected = isSelected,
        highlighted = checked,
        delay = delay,
        testing = testing,
        isBest = isBest,
        onClick = onSelect,
        onLongClick = onLongClick,
        modifier = modifier,
        titleIcon = when {
            chainLabel != null -> RowIcon(Icons.Default.Link, Res.string.chain)
            fromSubscription -> RowIcon(Icons.Default.Cloud, Res.string.subscription)
            else -> null
        },
        leading = handle
    ) {
        if (selecting) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
        } else {
            ServerMenu(actions)
        }
    }
}

@Composable
private fun ServerMenu(actions: ServerActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MenuTrailingButton(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            shapes = STANDALONE_MENU_SHAPES,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val close = { expanded = false }
            ServerMenuItem(Res.string.edit, Icons.Default.Edit, actions.onEdit, close)
            actions.onShare?.let { ServerMenuItem(Res.string.share, Icons.Default.Share, it, close) }
            actions.onRefreshSubscription?.let { ServerMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, it, close) }
            ServerMenuItem(Res.string.delete, Icons.Default.Delete, actions.onDelete, close)
        }
    }
}

@Composable
private fun ServerMenuItem(label: StringResource, icon: ImageVector, action: () -> Unit, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            action()
            close()
        }
    )
}
