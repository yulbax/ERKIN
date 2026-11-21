package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.ui.components.serverName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import io.github.yulbax.erkin.ui.components.AppDropdownMenu
import io.github.yulbax.erkin.ui.components.ChainIcon
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.filled.MoreVert

internal class ServerActions(
    val onEdit: () -> Unit,
    val onShare: (() -> Unit)?,
    val onRefreshSubscription: (() -> Unit)?,
    val onRemoveFromGroup: (() -> Unit)?,
    val onEditLogin: (() -> Unit)?,
    val onMenuEnd: () -> Unit,
    val menuEnd: ServerMenuEnd = ServerMenuEnd.DELETE,
    val menuEndEnabled: Boolean = true
)

internal enum class ServerMenuEnd(val label: StringResource, val icon: ImageVector) {
    DELETE(Res.string.delete, Icons.Default.Delete),
    HIDE(Res.string.hide_server, Icons.Default.VisibilityOff),
    SHOW(Res.string.show_server, Icons.Default.Visibility);

    companion object {
        fun of(profile: ProfileEntity, group: ServerGroupEntity?, groupServers: List<ProfileEntity>): ServerMenuEnd = when {
            profile.hidden -> SHOW
            group?.hidesServers == true && groupServers.size > 1 -> HIDE
            else -> DELETE
        }
    }

    fun allowedFor(profile: ProfileEntity, groupServers: List<ProfileEntity>): Boolean =
        this != HIDE || BulkAction.HIDE.keepsAServerVisible(listOf(profile.id), groupServers.filterNot { it.hidden }.mapTo(HashSet()) { it.id })
}

@Composable
internal fun ServerRow(
    profile: ProfileEntity,
    isSelected: Boolean,
    actions: ServerActions,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    result: ProbeResult? = null,
    testing: Boolean = false,
    isBest: Boolean = false,
    chainLabel: String? = null,
    groupIcon: RowIcon? = null,
    groupName: String? = null,
    mode: RowMode = RowMode.Normal
) {
    ServerRowLayout(
        title = serverName(profile.name),
        subtitle = listOfNotNull(groupName, chainLabel).joinToString(" · "),
        details = profile.type.takeIf { chainLabel == null },
        isSelected = isSelected,
        mode = mode,
        result = result,
        testing = testing,
        isBest = isBest,
        onClick = onSelect,
        onLongClick = onLongClick,
        modifier = modifier,
        titleIcon = when {
            chainLabel != null -> RowIcon(ChainIcon, Res.string.chain)
            else -> groupIcon
        },
        menu = { ServerMenu(actions) }
    )
}

@Composable
private fun ServerMenu(actions: ServerActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledRowButton(onClick = { expanded = true }, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.more_options_cd))
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val close = { expanded = false }
            ServerMenuItem(Res.string.edit, Icons.Default.Edit, actions.onEdit, close)
            actions.onShare?.let { ServerMenuItem(Res.string.share, Icons.Default.Share, it, close) }
            actions.onRefreshSubscription?.let { ServerMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, it, close) }
            actions.onRemoveFromGroup?.let { ServerMenuItem(Res.string.remove_from_group, Icons.Default.FolderOff, it, close) }
            actions.onEditLogin?.let { ServerMenuItem(Res.string.login_menu, Icons.Default.Key, it, close) }
            ServerMenuItem(actions.menuEnd.label, actions.menuEnd.icon, actions.onMenuEnd, close, enabled = actions.menuEndEnabled)
        }
    }
}

@Composable
private fun ServerMenuItem(label: StringResource, icon: ImageVector, action: () -> Unit, close: () -> Unit, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = {
            action()
            close()
        }
    )
}
