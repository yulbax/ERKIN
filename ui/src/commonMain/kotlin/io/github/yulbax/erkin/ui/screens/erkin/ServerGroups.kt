package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.ui.components.serverName
import io.github.yulbax.erkin.util.formatBytes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import io.github.yulbax.erkin.ui.screens.apps.warningColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionUsage
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.AppDropdownMenu
import io.github.yulbax.erkin.ui.components.ChoiceRow
import io.github.yulbax.erkin.ui.components.IconSource
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.vpn.core.ProbeResult
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal class GroupActions(
    val refreshing: Set<Long>,
    val waitingLists: Set<String>,
    val onOpen: (Long) -> Unit,
    val onTest: (servers: List<Long>) -> Deferred<List<Long>?>,
    val onRefresh: (Long) -> Unit,
    val onAdd: (Long) -> Unit,
    val onRename: (Long) -> Unit,
    val onShare: (Long) -> Unit,
    val onUngroup: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onRulesEnabled: (Long, Boolean) -> Unit
)

internal val GroupKind.rowIcon: RowIcon
    get() = when (this) {
        is GroupKind.Subscription -> RowIcon(IconSource.Vector(Icons.Default.Cloud), Res.string.subscription)
        GroupKind.Local -> RowIcon(IconSource.Vector(Icons.Default.Folder), Res.string.server_group)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupRow(
    item: ServerNode.Group,
    actions: GroupActions,
    isSelected: Boolean,
    result: ProbeResult?,
    testing: Boolean,
    isBest: Boolean,
    mode: RowMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ServerRowLayout(
        title = item.name,
        subtitle = serverName(item.current.name),
        details = "${item.members.size} · ${item.current.type}",
        isSelected = isSelected,
        mode = mode,
        result = result,
        testing = testing,
        isBest = isBest,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        titleIcon = item.kind.rowIcon,
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

@Composable
internal fun SubscriptionCard(group: ServerNode.Group, actions: GroupActions) {
    val usage = group.usage
    val notes = group.notes
    val traffic = usage?.takeIf { it.used > 0 || it.total > 0 }
    val expiry = usage?.takeIf { it.expire > 0 }
    val hasFacts = expiry != null || group.updatedAt != null
    val hasLinks = notes?.supportUrl != null || notes?.pageUrl != null
    val rules = group.rules?.takeUnless { it.isEmpty }
    if (traffic == null && !hasFacts && notes?.announce == null && !hasLinks && rules == null) return
    val facts: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            expiry?.let { ExpiryFact(it.expire) }
            group.updatedAt?.let { UpdatedFact(it, notes?.updateHours) }
        }
    }
    val sections = buildList<@Composable () -> Unit> {
        traffic?.let { add { TrafficSection(it) } }
        if (hasFacts) add(facts)
        notes?.announce?.let { text -> add { Announcement(text) } }
        rules?.let { add { RulesSection(group, it, actions) } }
        if (hasLinks) add { LinkButtons(notes.supportUrl, notes.pageUrl) }
    }
    val compact = buildList<@Composable () -> Unit> {
        traffic?.let { add { TrafficSection(it) } }
        when {
            traffic != null && expiry != null -> add { ExpiryFact(expiry.expire) }
            traffic == null && hasFacts -> add(facts)
        }
    }
    val expandable = sections.size > compact.size
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        enabled = expandable,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            Modifier.animateContentSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = if (expandable) 4.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (if (expanded || !expandable) sections else compact).forEach { it() }
            if (expandable) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) Res.string.subscription_card_less else Res.string.subscription_card_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RulesSection(group: ServerNode.Group, rules: SubscriptionRules, actions: GroupActions) {
    var opened by remember { mutableStateOf(false) }
    val summary = listOfNotNull(
        stringResource(Res.string.subscription_rules_after_yours),
        rules.rules.count { it.providers.isNotEmpty() }.takeIf { it > 0 }?.let { stringResource(Res.string.subscription_rules_from_lists, it) },
        rules.skipped.size.takeIf { it > 0 }?.let { stringResource(Res.string.subscription_rules_skipped, it) }
    ).joinToString(" · ")
    Column {
        Text(
            stringResource(Res.string.subscription_rules_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable { opened = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.subscription_rules_title, rules.rules.size), style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.padding(horizontal = 8.dp).size(width = 1.dp, height = 32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Switch(checked = group.rulesEnabled, onCheckedChange = { actions.onRulesEnabled(group.id, it) })
        }
    }
    if (opened) {
        SubscriptionRulesPage(
            subscriptionName = group.name,
            rules = rules,
            waitingLists = actions.waitingLists,
            enabled = group.rulesEnabled,
            onEnabledChange = { actions.onRulesEnabled(group.id, it) },
            onDismiss = { opened = false }
        )
    }
}

@Composable
private fun TrafficSection(usage: SubscriptionUsage) {
    if (usage.total <= 0) {
        Text(
            stringResource(Res.string.subscription_used, formatBytes(usage.used)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val fraction = (usage.used.toFloat() / usage.total).coerceIn(0f, 1f)
    val color = if (fraction >= WARNING_FRACTION) warningColor() else MaterialTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatBytes(usage.used), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(Res.string.subscription_of_total, formatBytes(usage.total)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.subscription_percent, (fraction * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
private fun ExpiryFact(expire: Long) {
    val millisLeft = expire * 1000 - System.currentTimeMillis()
    if (millisLeft <= 0) {
        Fact(
            icon = Icons.Filled.Event,
            title = stringResource(Res.string.subscription_expired),
            detail = null,
            accent = MaterialTheme.colorScheme.error
        )
        return
    }
    val days = ((millisLeft + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
    Fact(
        icon = Icons.Filled.Event,
        title = stringResource(
            Res.string.subscription_expires,
            DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(expire * 1000))
        ),
        detail = pluralStringResource(Res.plurals.subscription_days_left, days, days),
        accent = if (days <= EXPIRY_WARNING_DAYS) warningColor() else null
    )
}

@Composable
private fun UpdatedFact(updatedAt: Long, updateHours: Int?) {
    Fact(
        icon = Icons.Filled.Schedule,
        title = stringResource(Res.string.subscription_updated, updatedText(updatedAt)),
        detail = updateHours?.let { pluralStringResource(Res.plurals.subscription_update_every, it, it) }
    )
}

@Composable
private fun Fact(icon: ImageVector, title: String, detail: String?, accent: Color? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = accent ?: MaterialTheme.colorScheme.onSurface)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Announcement(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun LinkButtons(supportUrl: String?, pageUrl: String?) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        supportUrl?.let { LinkButton(Icons.Filled.SupportAgent, Res.string.subscription_support) { runCatching { uriHandler.openUri(it) } } }
        pageUrl?.let { LinkButton(Icons.AutoMirrored.Filled.OpenInNew, Res.string.subscription_page) { runCatching { uriHandler.openUri(it) } } }
    }
}

@Composable
private fun LinkButton(icon: ImageVector, label: StringResource, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

private const val WARNING_FRACTION = 0.9f
private const val EXPIRY_WARNING_DAYS = 7
private const val DAY_MILLIS = 86_400_000L

@Composable
internal fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
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
internal fun GroupMenuButton(item: ServerNode.Group, actions: GroupActions) {
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
private fun GroupMenuItems(item: ServerNode.Group, actions: GroupActions, withRefresh: Boolean, close: () -> Unit) {
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
    groups: List<ServerNode.Group>,
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

internal fun updatedText(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

