package io.github.yulbax.erkin.ui.screens.connections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import io.github.yulbax.erkin.vpn.connections.ServicePurpose
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.executableName
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.ui.components.AppDropdownMenu
import io.github.yulbax.erkin.vpn.core.RuleValues
import io.github.yulbax.erkin.ui.components.groupItemShape
import io.github.yulbax.erkin.ui.components.EmptyState
import io.github.yulbax.erkin.ui.components.AppIcon
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.platform.rememberCopyText
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.screens.apps.channelColor
import io.github.yulbax.erkin.ui.screens.rules.channel
import io.github.yulbax.erkin.ui.screens.rules.ruleTarget
import io.github.yulbax.erkin.ui.viewmodel.ConnectionApp
import io.github.yulbax.erkin.ui.viewmodel.ConnectionsViewModel
import io.github.yulbax.erkin.vpn.ProxyRef
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.ui.viewmodel.RulesViewModel
import io.github.yulbax.erkin.ui.screens.rules.RuleDialog
import io.github.yulbax.erkin.util.formatBytes
import io.github.yulbax.erkin.vpn.connections.ConnectionRoute
import io.github.yulbax.erkin.vpn.connections.HostActivity
import io.github.yulbax.erkin.vpn.connections.RouteOrigin
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SERVICE_NAME = "ERKIN"
private val CHANNEL_ORDER = listOf(ConnectionType.DIRECT, ConnectionType.BYEDPI, ConnectionType.VPN, ConnectionType.BLOCK)

@Composable
fun Connections(onOpenApp: (String) -> Unit, viewModel: ConnectionsViewModel = koinViewModel()) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val servers by viewModel.serverChoices.collectAsStateWithLifecycle()
    when {
        !running -> Placeholder(stringResource(Res.string.connections_off))
        apps.isEmpty() -> Placeholder(stringResource(Res.string.connections_empty))
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                TrafficSummary(
                    traffic = apps.map { it.activity.traffic }.fold(emptyMap()) { total, part ->
                        (total.keys + part.keys).associateWith { (total[it] ?: 0) + (part[it] ?: 0) }
                    },
                    attempts = apps.sumOf { it.activity.attempts },
                    caption = stringResource(Res.string.connections_summary, apps.size, apps.sumOf { it.activity.active }),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
            itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                AppRow(app, servers, index, apps.lastIndex) { onOpenApp(app.id) }
            }
        }
    }
}

@Composable
fun AppConnections(
    appId: String,
    viewModel: ConnectionsViewModel = koinViewModel(),
    rulesViewModel: RulesViewModel = koinViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val servers by viewModel.serverChoices.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.id == appId }
    if (app == null) {
        Placeholder(stringResource(Res.string.connections_empty))
        return
    }
    val rules by remember(app.ruleApp) { viewModel.rulesOf(app.ruleApp) }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    var ruleFor by remember { mutableStateOf<Pair<HostActivity, RuleScope>?>(null) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val copy = rememberCopyText(stringResource(Res.string.connections_copied))
    val choices by rulesViewModel.serverChoices.collectAsStateWithLifecycle()
    val ruleSets by rulesViewModel.listStates.collectAsStateWithLifecycle()
    val knownApps by rulesViewModel.apps.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AppHeader(app, servers) }
        item {
            TrafficSummary(
                traffic = app.activity.traffic,
                attempts = app.activity.attempts,
                caption = null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (!app.isService) item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                listOf(
                    stringResource(Res.string.connections_tab_hosts, app.activity.hosts.size),
                    stringResource(Res.string.connections_tab_rules, rules.size)
                ).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        if (tab == 0 || app.isService) {
            items(app.activity.hosts, key = { it.key }) { host ->
                HostRow(
                    host = host,
                    servers = servers,
                    expanded = host.key in expanded,
                    onToggle = { expanded = if (host.key in expanded) expanded - host.key else expanded + host.key },
                    onRule = { scope: RuleScope -> ruleFor = host to scope }.takeUnless { app.isService },
                    onCopy = copy
                )
            }
        } else if (rules.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.connections_no_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        } else {
            items(rules, key = { it.id }) { rule -> AppRuleRow(rule, onDelete = { viewModel.deleteRule(rule) }) }
        }
    }

    ruleFor?.let { (host, scope) ->
        RuleDialog(
            initial = host.ruleDraft(scope, app.ruleApp),
            ruleSets = ruleSets,
            choices = choices,
            apps = knownApps,
            onDismiss = { ruleFor = null },
            onSave = { match, raw, target, serverId, ruleApp ->
                ruleFor = null
                rulesViewModel.save(null, match, raw, target, serverId, ruleApp)
            },
            onDelete = null,
            onOpenRuleSets = { ruleFor = null },
            isNew = true
        )
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(text)
    }
}

@Composable
private fun AppRow(app: ConnectionApp, servers: ServerChoices, index: Int, lastIndex: Int, onClick: () -> Unit) {
    Surface(
        shape = groupItemShape(index, lastIndex + 1),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionAppIcon(app, 40.dp, Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.displayName(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.connections_app_line, app.activity.active, formatBytes(app.activity.up + app.activity.down)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (app.activity.attempts > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(Res.string.connections_blocked_badge, app.activity.attempts), channelColor(ConnectionType.BLOCK))
                    }
                }
            }
            app.channel?.let { channel ->
                Spacer(Modifier.width(8.dp))
                RouteChip(app.route(channel, servers), servers)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppHeader(app: ConnectionApp, servers: ServerChoices) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ConnectionAppIcon(app, 48.dp, Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.displayName(), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (app.isService) {
                Text(
                    stringResource(Res.string.connections_service_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            app.channel?.let { channel ->
                Text(
                    stringResource(Res.string.connections_channel, routeLabel(app.route(channel, servers), servers)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrafficSummary(traffic: Map<ConnectionType, Long>, attempts: Int, caption: String?, modifier: Modifier) {
    val shown = traffic.filterValues { it > 0 }
    val total = shown.values.sum()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatBytes(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.connections_session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                CHANNEL_ORDER.forEach { channel ->
                    val bytes = shown[channel] ?: return@forEach
                    Box(Modifier.weight(bytes.toFloat() / total).fillMaxHeight().background(channelColor(channel)))
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CHANNEL_ORDER.forEach { channel ->
                    val text = when {
                        channel == ConnectionType.BLOCK && attempts > 0 -> "${channel.label()} $attempts"
                        shown[channel] != null -> "${channel.label()} ${formatBytes(shown.getValue(channel))}"
                        else -> return@forEach
                    }
                    Legend(text, channelColor(channel))
                }
            }
            if (caption != null) {
                Spacer(Modifier.height(8.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

private sealed interface RuleScope {
    val value: String

    data class Address(override val value: String) : RuleScope

    data class Site(override val value: String) : RuleScope
}

private val HostActivity.key: String get() = "$host|$route|$origin|$purpose"

@Composable
private fun HostRow(
    host: HostActivity,
    servers: ServerChoices,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRule: ((RuleScope) -> Unit)?,
    onCopy: (String) -> Unit
) {
    val single = host.single
    val many = host.addresses.size > 1
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = many, onClick = onToggle).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(channelColor(host.route.channel)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(host.host, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val origin = when (host.origin) {
                    RouteOrigin.APP_CHANNEL -> null
                    RouteOrigin.RULE -> stringResource(Res.string.connections_by_rule)
                    RouteOrigin.APP_RULE -> stringResource(Res.string.connections_by_app_rule)
                }
                val purpose = when (host.purpose) {
                    null, ServicePurpose.CORE -> null
                    ServicePurpose.REQUEST -> stringResource(Res.string.connections_purpose_request)
                }
                val count = if (many) pluralStringResource(Res.plurals.connections_addresses, host.addresses.size, host.addresses.size) else null
                Text(
                    listOfNotNull(purpose, routeLabel(host.route, servers), origin, count).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                if (host.blocked) pluralStringResource(Res.plurals.connections_attempts, host.attempts, host.attempts) else "↓ ${formatBytes(host.down)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (many) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp)
                )
            }
            HostMenu(
                address = single?.name,
                site = host.host.takeUnless { RuleValues.isIpAddress(it) || (single != null && single.name == it) },
                onRule = onRule,
                onCopy = onCopy
            )
        }
        if (many && expanded) {
            host.addresses.forEach { address ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 40.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        address.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!host.blocked) {
                        Text("↓ ${formatBytes(address.down)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HostMenu(address = address.name, site = null, onRule = onRule, onCopy = onCopy)
                }
            }
        }
    }
}

@Composable
private fun HostMenu(address: String?, site: String?, onRule: ((RuleScope) -> Unit)?, onCopy: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.connections_more_cd), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onRule != null) {
                address?.let { value ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.connections_rule_menu)) }, onClick = { open = false; onRule(RuleScope.Address(value)) })
                }
                site?.let { value ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.connections_rule_site, value)) }, onClick = { open = false; onRule(RuleScope.Site(value)) })
                }
            }
            address?.let { value ->
                DropdownMenuItem(text = { Text(stringResource(Res.string.connections_copy)) }, onClick = { open = false; onCopy(value) })
            }
            if (address == null) {
                site?.let { value ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.connections_copy_site)) }, onClick = { open = false; onCopy(value) })
                }
            }
        }
    }
}

@Composable
private fun AppRuleRow(rule: RoutingRuleEntity, onDelete: () -> Unit) {
    val channel = RuleTarget.fromWire(rule.target)?.channel
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        channel?.let { ConnectionTypeIcon(it, Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            channel?.let { Text(it.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RouteChip(route: ConnectionRoute, servers: ServerChoices) {
    val color = channelColor(route.channel)
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            ConnectionTypeIcon(route.channel, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(routeLabel(route, servers), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 2.dp))
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun ConnectionAppIcon(app: ConnectionApp, size: Dp, modifier: Modifier) {
    if (app.isService) {
        ErkinIcon(size, modifier)
        return
    }
    AppIcon(app.ruleApp, app.path, app.displayName(), size, modifier)
}

@Composable
private fun ErkinIcon(size: Dp, modifier: Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4))
            .background(Brush.linearGradient(listOf(Color(0xFF30343B), Color(0xFF15171B)))),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(Res.drawable.ic_erkin), contentDescription = null, modifier = Modifier.fillMaxSize().padding(size / 6))
    }
}

private fun HostActivity.ruleDraft(scope: RuleScope, app: String): RoutingRuleEntity {
    val match = when {
        RuleValues.isIpAddress(scope.value) -> RuleMatch.IP_CIDR
        scope is RuleScope.Site -> RuleMatch.DOMAIN_SUFFIX
        else -> RuleMatch.DOMAIN
    }
    val target = if (blocked) RuleTarget.DIRECT else route.channel.ruleTarget ?: RuleTarget.DIRECT
    return RoutingRuleEntity(
        type = match.wire,
        value = scope.value,
        target = target.wire,
        serverId = route.serverId.takeIf { target == RuleTarget.VPN },
        app = app
    )
}

@Composable
private fun ConnectionApp.displayName(): String =
    if (isService) SERVICE_NAME else name ?: executableName(id).ifEmpty { stringResource(Res.string.connections_unknown_app) }

private fun ConnectionApp.route(channel: ConnectionType, choices: ServerChoices): ConnectionRoute {
    val bound = choices.current(channel, serverId) as? CurrentServer.Bound
    return ConnectionRoute(channel, bound?.let { ProxyRef.Profile(it.serverId) })
}

@Composable
private fun routeLabel(route: ConnectionRoute, choices: ServerChoices): String {
    val server = route.server?.takeIf { route.channel == ConnectionType.VPN }?.let(choices::labelOf) ?: return route.channel.label()
    return if (server.unreachable) "${server.name} · ${stringResource(Res.string.apps_server_unreachable)}" else server.name
}
