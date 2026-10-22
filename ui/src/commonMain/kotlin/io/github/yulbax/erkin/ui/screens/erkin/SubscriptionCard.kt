package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubscriptionRow(
    name: String,
    count: Int,
    current: ProfileEntity,
    isSelected: Boolean,
    delay: ProxyDelay?,
    testing: Boolean,
    isBest: Boolean,
    selecting: Boolean,
    checkState: ToggleableState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    menu: SubscriptionMenu,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val pingColor = delay?.let(::pingColor)
    Surface(
        shape = shape,
        color = when {
            checkState != ToggleableState.Off -> MaterialTheme.colorScheme.secondaryContainer
            isBest && pingColor != null -> pingColor.copy(alpha = 0.14f)
            isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { selected = isSelected }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndicatorBar(isSelected)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    current.name.ifEmpty { stringResource(Res.string.unnamed_profile) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = stringResource(Res.string.subscription),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$name · ${current.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    DelaySuffix(delay = delay, testing = testing)
                }
            }
            if (selecting) {
                TriStateCheckbox(state = checkState, onClick = null)
            } else {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(start = 12.dp, end = 4.dp)) {
                    Text(count.toString(), style = MaterialTheme.typography.labelLarge)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.subscription_show_servers)
                    )
                }
                SubscriptionMenuButton(menu)
            }
        }
    }
}

internal class SubscriptionMenu(
    val refreshing: Boolean,
    val onRefresh: () -> Unit,
    val onRename: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionSheet(
    name: String,
    usage: SubscriptionUsage?,
    members: List<ProfileEntity>,
    menu: SubscriptionMenu,
    onDismiss: () -> Unit,
    row: @Composable (ProfileEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = if (members.size <= SHORT_LIST) setOf(SheetValue.Hidden, SheetValue.Expanded) else SheetValue.entries.toSet()
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(Res.plurals.subscription_servers, members.size, members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (menu.refreshing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = menu.onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh_subscription))
                }
            }
            SubscriptionMenuButton(menu, inSheet = true)
        }
        usage?.let { UsageBlock(it) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(members, key = { it.id }) { row(it) }
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
private fun IndicatorBar(visible: Boolean) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (visible) MaterialTheme.colorScheme.primary else Color.Transparent)
    )
}

@Composable
private fun SubscriptionMenuButton(menu: SubscriptionMenu, inSheet: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.more_options_cd))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!inSheet) {
                SubscriptionMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, enabled = !menu.refreshing) {
                    menu.onRefresh()
                    expanded = false
                }
            }
            SubscriptionMenuItem(Res.string.rule_set_rename, Icons.Default.DriveFileRenameOutline) {
                menu.onRename()
                expanded = false
            }
            SubscriptionMenuItem(Res.string.share, Icons.Default.Share) {
                menu.onShare()
                expanded = false
            }
            SubscriptionMenuItem(Res.string.delete, Icons.Default.Delete) {
                menu.onDelete()
                expanded = false
            }
        }
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

internal fun SubscriptionEntity?.nameOr(url: String): String = this?.displayName ?: SubscriptionEntity.hostOf(url)
