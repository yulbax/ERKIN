package io.github.yulbax.erkin.ui.screens.erkin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage
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

internal class SubscriptionActions(
    val refreshing: Set<Long>,
    val onOpen: (Long) -> Unit,
    val onRefresh: (Long) -> Unit,
    val onRename: (Long) -> Unit,
    val onShare: (Long) -> Unit,
    val onDelete: (Long) -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubscriptionRow(
    item: ServerListItem.Subscription,
    actions: SubscriptionActions,
    isSelected: Boolean,
    delay: ProxyDelay?,
    testing: Boolean,
    isBest: Boolean,
    selecting: Boolean,
    checkState: ToggleableState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null
) {
    ServerRowLayout(
        title = item.current.name.ifEmpty { stringResource(Res.string.unnamed_profile) },
        subtitle = "${item.name} · ${item.members.size} · ${item.current.type}",
        isSelected = isSelected,
        highlighted = checkState != ToggleableState.Off,
        delay = delay,
        testing = testing,
        isBest = isBest,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        subtitleIcon = RowIcon(Icons.Default.Cloud, Res.string.subscription),
        leading = if (selecting) ({ TriStateCheckbox(state = checkState, onClick = null) }) else null
    ) {
        if (selecting) {
            handle?.invoke()
        } else {
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
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            SubscriptionMenuItems(item.id, actions, withRefresh = true) { menuExpanded = false }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionSheet(
    item: ServerListItem.Subscription,
    actions: SubscriptionActions,
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
                    pluralStringResource(Res.plurals.subscription_servers, item.members.size, item.members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.id in actions.refreshing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = { actions.onRefresh(item.id) }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh_subscription))
                }
            }
            SubscriptionMenuButton(item, actions)
        }
        item.usage?.let { UsageBlock(it) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
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
private fun SubscriptionMenuButton(item: ServerListItem.Subscription, actions: SubscriptionActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.more_options_cd))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SubscriptionMenuItems(item.id, actions, withRefresh = false) { expanded = false }
        }
    }
}

@Composable
private fun SubscriptionMenuItems(id: Long, actions: SubscriptionActions, withRefresh: Boolean, close: () -> Unit) {
    if (withRefresh) {
        SubscriptionMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, enabled = id !in actions.refreshing) {
            actions.onRefresh(id)
            close()
        }
    }
    SubscriptionMenuItem(Res.string.rename, Icons.Default.DriveFileRenameOutline) {
        actions.onRename(id)
        close()
    }
    SubscriptionMenuItem(Res.string.share, Icons.Default.Share) {
        actions.onShare(id)
        close()
    }
    SubscriptionMenuItem(Res.string.delete, Icons.Default.Delete) {
        actions.onDelete(id)
        close()
    }
}

@Composable
private fun SubscriptionMenuItem(label: StringResource, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), if (value < 10) "%.1f %s" else "%.0f %s", value, BYTE_UNITS[unit])
}

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")
private const val SHORT_LIST = 6
