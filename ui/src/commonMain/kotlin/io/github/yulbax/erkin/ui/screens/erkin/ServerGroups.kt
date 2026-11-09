package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.ui.components.serverName
import io.github.yulbax.erkin.util.formatBytes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.Deferred
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.AppDropdownMenu
import io.github.yulbax.erkin.ui.components.ChoiceRow
import io.github.yulbax.erkin.ui.components.IconSource
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.ServerListItem
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal class GroupActions(
    val refreshing: Set<Long>,
    val testing: Boolean,
    val onOpen: (Long) -> Unit,
    val onTest: (servers: List<Long>) -> Deferred<List<Long>?>,
    val onRefresh: (Long) -> Unit,
    val onAdd: (Long) -> Unit,
    val onRename: (Long) -> Unit,
    val onShare: (Long) -> Unit,
    val onUngroup: (Long) -> Unit,
    val onDelete: (Long) -> Unit
)

internal val GroupKind.rowIcon: RowIcon
    get() = when (this) {
        is GroupKind.Subscription -> RowIcon(IconSource.Vector(Icons.Default.Cloud), Res.string.subscription)
        GroupKind.Local -> RowIcon(IconSource.Vector(Icons.Default.Folder), Res.string.server_group)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupRow(
    item: ServerListItem.Group,
    actions: GroupActions,
    isSelected: Boolean,
    delay: ProxyDelay?,
    testing: Boolean,
    isBest: Boolean,
    mode: RowMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ServerRowLayout(
        title = serverName(item.current.name),
        subtitle = "${item.name} · ${item.members.size} · ${item.current.type}",
        isSelected = isSelected,
        mode = mode,
        delay = delay,
        testing = testing,
        isBest = isBest,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        subtitleIcon = item.kind.rowIcon,
        menu = {
            var menuExpanded by remember { mutableStateOf(false) }
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = { actions.onOpen(item.id) }, colors = splitButtonColors()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(Res.string.subscription_show_servers),
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                        )
                    }
                },
                trailingButton = {
                    Box {
                        MenuTrailingButton(expanded = menuExpanded, onExpandedChange = { menuExpanded = it })
                        AppDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            GroupMenuItems(item, actions, withRefresh = true) { menuExpanded = false }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupSheet(
    item: ServerListItem.Group,
    actions: GroupActions,
    onDismiss: () -> Unit,
    row: @Composable (profile: ProfileEntity, close: () -> Unit) -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = if (item.members.size <= SHORT_LIST) setOf(SheetValue.Hidden, SheetValue.Expanded) else SheetValue.entries.toSet()
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val listState = rememberLazyListState()
        val scrollAfterSort = rememberScrollToTopAfterSort(listState, item.members.map { it.id })
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        pluralStringResource(Res.plurals.subscription_servers, item.members.size, item.members.size),
                        item.updatedAt?.let { stringResource(Res.string.subscription_updated, updatedText(it)) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (item.kind) {
                is GroupKind.Subscription -> RefreshButton(refreshing = item.id in actions.refreshing) { actions.onRefresh(item.id) }
                GroupKind.Local -> IconButton(onClick = { actions.onAdd(item.id) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_server))
                }
            }
            TestGroupButton(testing = actions.testing) { scrollAfterSort(actions.onTest(item.members.map { it.id })) }
            GroupMenuButton(item, actions)
        }
        item.usage?.let { UsageBlock(it) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(item.members, key = { it.id }) { row(it, close) }
        }
    }
}

@Composable
private fun UsageBlock(usage: SubscriptionUsage) {
    val text = usageText(usage) ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (usage.total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (usage.used.toFloat() / usage.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    if (refreshing) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh_subscription))
        }
    }
}

@Composable
private fun TestGroupButton(testing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !testing) {
        Icon(Icons.Default.Speed, contentDescription = stringResource(Res.string.test_subscription))
    }
}

@Composable
private fun GroupMenuButton(item: ServerListItem.Group, actions: GroupActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.more_options_cd))
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupMenuItems(item, actions, withRefresh = false) { expanded = false }
        }
    }
}

@Composable
private fun GroupMenuItems(item: ServerListItem.Group, actions: GroupActions, withRefresh: Boolean, close: () -> Unit) {
    val id = item.id
    if (withRefresh && item.kind is GroupKind.Subscription) {
        GroupMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, enabled = id !in actions.refreshing) {
            actions.onRefresh(id)
            close()
        }
    }
    GroupMenuItem(Res.string.rename, Icons.Default.DriveFileRenameOutline) {
        actions.onRename(id)
        close()
    }
    GroupMenuItem(Res.string.share, Icons.Default.Share) {
        actions.onShare(id)
        close()
    }
    if (item.kind == GroupKind.Local) {
        GroupMenuItem(Res.string.ungroup, Icons.Default.FolderOff) {
            actions.onUngroup(id)
            close()
        }
    }
    GroupMenuItem(Res.string.delete, Icons.Default.Delete) {
        actions.onDelete(id)
        close()
    }
}

@Composable
internal fun MoveToGroupDialog(
    groups: List<ServerListItem.Group>,
    onDismiss: () -> Unit,
    onNewGroup: () -> Unit,
    onGroup: (Long) -> Unit
) {
    AppDialog(
        title = stringResource(Res.string.move_to_group),
        onDismiss = onDismiss,
        buttons = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } }
    ) {
        ChoiceRow(
            title = stringResource(Res.string.new_group),
            checked = false,
            onClick = onNewGroup,
            leading = { GroupChoiceIcon(Icons.Default.Add) }
        )
        groups.forEach { group ->
            ChoiceRow(
                title = group.name,
                subtitle = pluralStringResource(Res.plurals.subscription_servers, group.members.size, group.members.size),
                checked = false,
                onClick = { onGroup(group.id) },
                leading = { GroupChoiceIcon(Icons.Default.Folder) }
            )
        }
    }
}

@Composable
private fun GroupChoiceIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
}

@Composable
private fun GroupMenuItem(label: StringResource, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun usageText(usage: SubscriptionUsage): String? {
    val traffic = when {
        usage.total > 0 -> stringResource(Res.string.subscription_usage, formatBytes(usage.used), formatBytes(usage.total))
        usage.used > 0 -> stringResource(Res.string.subscription_used, formatBytes(usage.used))
        else -> null
    }
    val expiry = when {
        usage.expire <= 0 -> null
        usage.expire * 1000 < System.currentTimeMillis() -> stringResource(Res.string.subscription_expired)
        else -> stringResource(
            Res.string.subscription_expires,
            DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(usage.expire * 1000))
        )
    }
    return listOfNotNull(traffic, expiry).joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun updatedText(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

private const val SHORT_LIST = 6
