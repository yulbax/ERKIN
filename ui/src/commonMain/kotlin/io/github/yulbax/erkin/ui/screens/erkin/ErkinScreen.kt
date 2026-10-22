package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.res.local_network_denied
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.AddFab
import io.github.yulbax.erkin.ui.components.HintBanner
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.components.groupRowShape
import io.github.yulbax.erkin.ui.components.RenameDialog
import androidx.compose.ui.state.ToggleableState
import io.github.yulbax.erkin.ui.platform.rememberSharer
import io.github.yulbax.erkin.ui.screens.chains.ChainDialog
import io.github.yulbax.erkin.ui.screens.chains.chainLabel
import io.github.yulbax.erkin.ui.platform.rememberLocalNetworkRequest
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.platform.rememberVpnStartRequest
import io.github.yulbax.erkin.ui.viewmodel.ServerError
import io.github.yulbax.erkin.ui.viewmodel.ChainsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ConnectionViewModel
import io.github.yulbax.erkin.ui.viewmodel.ErkinUiState
import io.github.yulbax.erkin.ui.viewmodel.ProfileViewModel
import io.github.yulbax.erkin.ui.viewmodel.ServerListItem
import io.github.yulbax.erkin.ui.viewmodel.ServersUiState
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.VpnState
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ErkinScreen(
    onNavigateToApps: () -> Unit = {},
    viewModel: ConnectionViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel(),
    chainsViewModel: ChainsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by profileViewModel.uiState.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    val shareChooserTitle = stringResource(Res.string.share_server_chooser)

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var editingChain by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleting by remember { mutableStateOf<List<ProfileEntity>?>(null) }
    var checkedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    val checked = checkedIds.filterTo(HashSet()) { id -> servers.profiles.any { it.id == id } }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = checked.isNotEmpty(),
        onBackCompleted = { checkedIds = emptyList() }
    )
    var showByeDpiDialog by remember { mutableStateOf(false) }
    val byeDpiTest by viewModel.byeDpiTest.collectAsStateWithLifecycle()

    val startVpn = rememberVpnStartRequest(onGranted = viewModel::startVpn)
    val showMessage = rememberShowMessage()
    val localNetworkDenied = stringResource(Res.string.local_network_denied)
    val requestLocalNetwork = rememberLocalNetworkRequest { granted ->
        if (!granted) showMessage(localNetworkDenied)
        startVpn()
    }
    val onConnectClick = { if (ui.bypassLan) requestLocalNetwork() else startVpn() }

    val connected = state.isActive

    val content = ErkinScreenContent(
        ui = ui,
        state = state,
        stats = stats,
        servers = servers,
        connected = connected,
        onToggle = { if (connected) viewModel.stopVpn() else onConnectClick() },
        onDismissHint = { viewModel.dismissHomeHint() },
        onNavigateToApps = onNavigateToApps,
        onByedpiClick = { showByeDpiDialog = true },
        onAddServerClick = { showAddDialog = true },
        onTestClick = { profileViewModel.testAll() },
        onSelect = { profileViewModel.select(it) },
        onEdit = { profile ->
            when (profile.kind) {
                is ServerKind.Link -> editing = profile
                is ServerKind.Chain -> editingChain = profile
            }
        },
        onShare = { profile -> sharer.shareText(profile.link, shareChooserTitle) },
        onDelete = { deleting = listOf(it) },
        subscriptions = SubscriptionActions(
            refreshing = servers.refreshing,
            onOpen = { sheetId = it },
            onRefresh = profileViewModel::refreshSubscription,
            onRename = { renamingId = it },
            onShare = { id -> servers.subscriptions[id]?.let { sharer.shareText(it.url, shareChooserTitle) } },
            onDelete = { id -> deleting = servers.profiles.filter { it.subscriptionId == id } }
        ),
        checked = checked,
        onToggleCheck = { profile ->
            checkedIds = if (profile.id in checked) checked.minus(profile.id).toList() else checked.plus(profile.id).toList()
        },
        onToggleChecks = { profiles ->
            val ids = profiles.map { it.id }
            checkedIds = if (checked.containsAll(ids)) (checked - ids.toSet()).toList() else (checked + ids).toList()
        },
        onCheckAll = { checkedIds = servers.profiles.map { it.id } },
        onClearChecks = { checkedIds = emptyList() },
        onDeleteChecked = { deleting = servers.profiles.filter { it.id in checked } }
    )

    val windowSize = LocalWindowInfo.current.containerSize
    val landscape = windowSize.width > windowSize.height
    if (landscape) ErkinScreenLandscape(content) else ErkinScreenPortrait(content)

    sheetId?.let { id ->
        val item = servers.subscription(id)
        if (item == null) {
            LaunchedEffect(id) { sheetId = null }
        } else {
            SubscriptionSheet(item, content.subscriptions, onDismiss = { sheetId = null }) { profile, close ->
                ServerListRow(content, ServerListItem.Server(profile), fastestId(servers.delays), Modifier, onSelected = close)
            }
        }
    }

    if (showByeDpiDialog) {
        LaunchedEffect(Unit) { viewModel.runByeDpiTest(full = false) }
        ByeDpiTestDialog(
            test = byeDpiTest,
            onRunFull = { viewModel.runByeDpiTest(full = true) },
            onDismiss = {
                viewModel.stopByeDpiTest()
                showByeDpiDialog = false
            }
        )
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onReadFile = { file, onRead -> profileViewModel.readConfigFile(file::open, onRead) },
            onAdd = { raw, fileName -> profileViewModel.add(raw, fileName); showAddDialog = false }
        )
    }

    editingChain?.let { chain ->
        val chainInvalid = stringResource(Res.string.chain_invalid)
        ChainDialog(
            initial = chain,
            profiles = servers.profiles,
            onDismiss = { editingChain = null },
            onSave = { name, spec ->
                chainsViewModel.save(chain, name, spec) { saved -> if (saved) editingChain = null else showMessage(chainInvalid) }
            },
            onDelete = {
                editingChain = null
                deleting = listOf(chain)
            }
        )
    }

    renamingId?.let { id ->
        val stored = servers.subscriptions[id]
        if (stored == null) {
            LaunchedEffect(id) { renamingId = null }
        } else {
            RenameDialog(
                current = stored.name.orEmpty(),
                placeholder = stored.title ?: SubscriptionEntity.hostOf(stored.url),
                onDismiss = { renamingId = null },
                onSave = { name ->
                    profileViewModel.renameSubscription(id, name)
                    renamingId = null
                }
            )
        }
    }

    editing?.let { profile ->
        EditServerDialog(
            profile = profile,
            onDismiss = { editing = null },
            onSave = { name, link ->
                profileViewModel.update(profile, name, link)
                editing = null
            }
        )
    }

    deleting?.let { profiles ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileViewModel.delete(profiles)
                        checkedIds = checkedIds - profiles.mapTo(HashSet()) { it.id }
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.cancel)) }
            },
            title = {
                val title = if (profiles.size == 1) Res.string.delete_server_title else Res.string.delete_servers_title
                Text(stringResource(title))
            },
            text = {
                val last = profiles.size >= servers.profiles.size && ui.hasVpnApps
                val single = profiles.singleOrNull()
                if (single != null) {
                    val displayName = single.name.ifEmpty { stringResource(Res.string.unnamed_profile) }
                    val message = if (last) Res.string.delete_last_server_message else Res.string.delete_server_message
                    Text(stringResource(message, displayName))
                } else {
                    val message = if (last) Res.string.delete_all_servers_message else Res.string.delete_servers_message
                    Text(stringResource(message, profiles.size))
                }
            }
        )
    }

    servers.error?.let { error ->
        val message = when (error) {
            ServerError.InvalidLink -> stringResource(Res.string.invalid_link_error)
            ServerError.EmptySubscription -> stringResource(Res.string.no_servers_in_subscription)
            is ServerError.FetchFailed -> stringResource(Res.string.fetch_subscription_failed, error.reason)
        }
        AlertDialog(
            onDismissRequest = { profileViewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { profileViewModel.clearError() }) { Text(stringResource(Res.string.ok)) }
            },
            title = { Text(stringResource(Res.string.dialog_error)) },
            text = { Text(message) }
        )
    }
}

private class ErkinScreenContent(
    val ui: ErkinUiState,
    val state: VpnState,
    val stats: ConnectionStats,
    val servers: ServersUiState,
    val connected: Boolean,
    val onToggle: () -> Unit,
    val onDismissHint: () -> Unit,
    val onNavigateToApps: () -> Unit,
    val onByedpiClick: () -> Unit,
    val onAddServerClick: () -> Unit,
    val onTestClick: () -> Unit,
    val onSelect: (ProfileEntity) -> Unit,
    val onEdit: (ProfileEntity) -> Unit,
    val onShare: (ProfileEntity) -> Unit,
    val onDelete: (ProfileEntity) -> Unit,
    val subscriptions: SubscriptionActions,
    val checked: Set<Long>,
    val onToggleCheck: (ProfileEntity) -> Unit,
    val onToggleChecks: (List<ProfileEntity>) -> Unit,
    val onCheckAll: () -> Unit,
    val onClearChecks: () -> Unit,
    val onDeleteChecked: () -> Unit
) {
    val selecting: Boolean get() = checked.isNotEmpty()
}

@Composable
private fun ErkinScreenPortrait(c: ErkinScreenContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        ConnectionSection(c)

        Spacer(Modifier.height(24.dp))
        ServerListSection(
            c = c,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        if (c.servers.profiles.isNotEmpty()) Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ErkinScreenLandscape(c: ErkinScreenContent) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionSection(c, landscape = true)
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ServerListSection(
                c = c,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                showHeader = false
            )
        }
    }
}

@Composable
private fun ColumnScope.ConnectionSection(c: ErkinScreenContent, landscape: Boolean = false) {
    if (!c.ui.homeHintSeen) {
        HintBanner(
            text = stringResource(Res.string.home_hint),
            actionLabel = stringResource(Res.string.home_hint_action),
            onAction = {
                c.onDismissHint()
                c.onNavigateToApps()
            },
            onDismiss = c.onDismissHint
        )
        Spacer(Modifier.height(12.dp))
    }

    val canStart = c.ui.hasByedpiApps || (c.servers.selected != null && c.ui.hasRoutedApps)
    val enabled = c.state != VpnState.Connecting && (c.connected || canStart)

    if (landscape) {
        ConnectionCard(
            state = c.state,
            stats = c.stats,
            connected = c.connected,
            enabled = enabled,
            onToggle = c.onToggle,
            modifier = Modifier.weight(2f).fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        ChannelsRow(c, modifier = Modifier.weight(1f).fillMaxWidth(), fillHeight = true)
    } else {
        ConnectionCard(
            state = c.state,
            stats = c.stats,
            connected = c.connected,
            enabled = enabled,
            onToggle = c.onToggle
        )

        Spacer(Modifier.height(20.dp))
        ChannelsRow(c)
    }

    if (!c.connected && !c.ui.hasRoutedApps) {
        Spacer(Modifier.height(12.dp))
        AddAppsCard(onClick = c.onNavigateToApps)
    }
}

@Composable
private fun ChannelsRow(c: ErkinScreenContent, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val cardModifier = if (fillHeight) Modifier.weight(1f).fillMaxHeight() else Modifier.weight(1f)
        ChannelStatusCard(
            type = ConnectionType.VPN,
            active = c.connected,
            up = c.stats.vpnUp,
            hasApps = c.ui.hasVpnApps,
            noServers = c.servers.profiles.isEmpty(),
            latencyMs = c.stats.vpnLatencyMs,
            country = c.stats.vpnCountry,
            shape = groupRowShape(0, 2),
            modifier = cardModifier,
            fillHeight = fillHeight
        )
        ChannelStatusCard(
            type = ConnectionType.BYEDPI,
            active = c.stats.byedpiActive,
            up = c.stats.byedpiUp,
            hasApps = c.ui.hasByedpiApps,
            shape = groupRowShape(1, 2),
            modifier = cardModifier,
            fillHeight = fillHeight,
            reachable = c.stats.byedpiReachable,
            total = c.stats.byedpiTotal,
            checking = c.stats.byedpiChecking,
            onClick = if (c.stats.byedpiActive) c.onByedpiClick else null
        )
    }
}

@Composable
private fun ServerListSection(c: ErkinScreenContent, modifier: Modifier, showHeader: Boolean = true) {
    if (showHeader) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.servers_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp)
            )
            TextButton(
                onClick = c.onAddServerClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.add_server))
            }
        }
    }

    if (c.servers.profiles.isEmpty()) {
        if (showHeader) {
            Text(
                text = stringResource(Res.string.servers_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.servers_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddFab(
                    onClick = c.onAddServerClick,
                    contentDescription = stringResource(Res.string.add_server),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        val showTest = c.servers.profiles.isNotEmpty()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    end = 6.dp,
                    top = 6.dp,
                    bottom = if (showTest || !showHeader) 64.dp else 6.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val fastestId = fastestId(c.servers.delays)
                items(c.servers.items, key = { it.key }) { item ->
                    when (item) {
                        is ServerListItem.Server -> ServerListRow(c, item, fastestId, Modifier.animateItem())
                        is ServerListItem.Subscription -> SubscriptionListRow(c, item, fastestId, Modifier.animateItem())
                    }
                }
            }
            AnimatedSpeedometerButton(visible = showTest && !c.selecting, testing = c.servers.testing, onClick = c.onTestClick)
            SelectionBar(c.selecting, c.checked.size, c.onClearChecks, c.onCheckAll, c.onDeleteChecked)
            if (!showHeader && !c.selecting) {
                AddFab(
                    onClick = c.onAddServerClick,
                    contentDescription = stringResource(Res.string.add_server),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AddAppsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(Res.string.connection_no_routed_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun BoxScope.AnimatedSpeedometerButton(visible: Boolean, testing: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
    ) {
        SpeedometerButton(testing = testing, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedometerButton(testing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = !testing,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (testing) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = stringResource(Res.string.server_test_latency),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerListRow(
    c: ErkinScreenContent,
    item: ServerListItem.Server,
    fastestId: Long?,
    modifier: Modifier,
    onSelected: () -> Unit = {}
) {
    val profile = item.profile
    val chainLabel = chainLabel(profile, c.servers.profiles)
    ServerRow(
        profile = profile,
        isSelected = profile.id == c.servers.selected?.id,
        actions = ServerActions(
            onEdit = { c.onEdit(profile) },
            onShare = if (chainLabel == null) ({ c.onShare(profile) }) else null,
            onRefreshSubscription = item.subscriptionId?.let { id -> { c.subscriptions.onRefresh(id) } },
            onDelete = { c.onDelete(profile) }
        ),
        onSelect = {
            if (c.selecting) {
                c.onToggleCheck(profile)
            } else {
                c.onSelect(profile)
                onSelected()
            }
        },
        onLongClick = { c.onToggleCheck(profile) },
        modifier = modifier,
        delay = c.servers.delays[profile.id],
        testing = profile.id in c.servers.testingIds,
        isBest = profile.id == fastestId,
        chainLabel = chainLabel,
        fromSubscription = item.subscriptionId != null,
        selecting = c.selecting,
        checked = profile.id in c.checked
    )
}

@Composable
private fun SubscriptionListRow(c: ErkinScreenContent, item: ServerListItem.Subscription, fastestId: Long?, modifier: Modifier) {
    val current = item.current
    val checkedCount = item.members.count { it.id in c.checked }
    SubscriptionRow(
        item = item,
        actions = c.subscriptions,
        isSelected = current.id == c.servers.selected?.id,
        delay = c.servers.delays[current.id],
        testing = current.id in c.servers.testingIds,
        isBest = current.id == fastestId,
        selecting = c.selecting,
        checkState = when (checkedCount) {
            0 -> ToggleableState.Off
            item.members.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        },
        onClick = { if (c.selecting) c.onToggleChecks(item.members) else c.onSelect(current) },
        onLongClick = { c.onToggleChecks(item.members) },
        modifier = modifier
    )
}

private fun fastestId(delays: Map<Long, ProxyDelay>): Long? = delays
    .mapNotNull { (id, delay) -> (delay as? ProxyDelay.Measured)?.let { id to it.ms } }
    .minByOrNull { it.second }
    ?.first
