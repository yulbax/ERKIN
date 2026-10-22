package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
internal fun SubscriptionHeader(
    name: String,
    usage: SubscriptionUsage?,
    count: Int,
    selected: ProfileEntity?,
    delay: ProxyDelay?,
    testing: Boolean,
    expanded: Boolean,
    refreshing: Boolean,
    selecting: Boolean,
    checkState: ToggleableState,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "subscriptionArrow")
    Surface(
        shape = shape,
        color = when {
            checkState != ToggleableState.Off -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected != null }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected != null && !expanded) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = stringResource(Res.string.subscription),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val servers = pluralStringResource(Res.plurals.subscription_servers, count, count)
                    Text(
                        if (selected != null) "$servers · ${selected.name.ifEmpty { stringResource(Res.string.unnamed_profile) }}" else servers,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (selected != null) DelaySuffix(delay = delay, testing = testing)
                }
                usage?.let { usageText(it) }?.let { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when {
                selecting -> TriStateCheckbox(state = checkState, onClick = null)
                refreshing -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                else -> Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) Res.string.subscription_hide_servers else Res.string.subscription_show_servers
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).rotate(arrow)
                )
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.more_options_cd)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        SubscriptionMenuItem(Res.string.refresh_subscription, Icons.Default.Refresh, enabled = !refreshing) {
                            onRefresh()
                            menuExpanded = false
                        }
                        SubscriptionMenuItem(Res.string.rule_set_rename, Icons.Default.DriveFileRenameOutline) {
                            onRename()
                            menuExpanded = false
                        }
                        SubscriptionMenuItem(Res.string.share, Icons.Default.Share) {
                            onShare()
                            menuExpanded = false
                        }
                        SubscriptionMenuItem(Res.string.delete, Icons.Default.Delete) {
                            onDelete()
                            menuExpanded = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionMenuItem(
    label: StringResource,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
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

internal fun SubscriptionEntity?.nameOr(url: String): String = this?.displayName ?: SubscriptionEntity.hostOf(url)
