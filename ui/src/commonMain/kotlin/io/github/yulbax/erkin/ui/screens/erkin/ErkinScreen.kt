package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.ui.components.Selection
import io.github.yulbax.erkin.ui.components.LoginDialog
import io.github.yulbax.erkin.ui.components.rememberSelection
import io.github.yulbax.erkin.ui.components.DestructiveConfirmDialog
import io.github.yulbax.erkin.ui.components.serverName
import io.github.yulbax.erkin.ui.components.serverNames
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.res.local_network_denied
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.canJoinGroup
import io.github.yulbax.erkin.data.profile.SubscriptionRefusal
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.RenameDialog
import io.github.yulbax.erkin.ui.platform.rememberSharer
import io.github.yulbax.erkin.ui.screens.chains.ChainDialog
import io.github.yulbax.erkin.ui.platform.rememberLocalNetworkRequest
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.platform.rememberVpnStartRequest
import io.github.yulbax.erkin.ui.viewmodel.ServerError
import io.github.yulbax.erkin.ui.viewmodel.ChainsViewModel
import io.github.yulbax.erkin.ui.viewmodel.ConnectionViewModel
import io.github.yulbax.erkin.ui.viewmodel.ErkinUiState
import io.github.yulbax.erkin.ui.viewmodel.ProfileViewModel
import io.github.yulbax.erkin.ui.viewmodel.LoginRequest
import io.github.yulbax.erkin.ui.viewmodel.IncomingServers
import org.koin.compose.koinInject
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.data.profile.ServerFolder
import io.github.yulbax.erkin.ui.viewmodel.ServersUiState
import io.github.yulbax.erkin.vpn.ConnectionStats
import io.github.yulbax.erkin.vpn.VpnState
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.Deferred
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.getPluralString

@Composable
fun ErkinScreen(
    onNavigateToApps: () -> Unit = {},
    onNavigateToByeDpi: () -> Unit = {},
    viewModel: ConnectionViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel(),
    chainsViewModel: ChainsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by profileViewModel.uiState.collectAsStateWithLifecycle()
    val login by profileViewModel.login.collectAsStateWithLifecycle()
    val incoming = koinInject<IncomingServers>()
    val incomingServer by incoming.next.collectAsStateWithLifecycle()
    LaunchedEffect(incomingServer) {
        incoming.take()?.let { profileViewModel.add(it.raw, it.fileName) }
    }
    val chainChoices by chainsViewModel.serverChoices.collectAsStateWithLifecycle()
    val sharer = rememberSharer()
    val shareChooserTitle = stringResource(Res.string.share_server_chooser)

    var adding by remember { mutableStateOf<AddRequest?>(null) }
    var moving by remember { mutableStateOf<MoveRequest?>(null) }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var editingChain by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleting by remember { mutableStateOf<List<ProfileEntity>?>(null) }
    var sheetId by remember { mutableStateOf<Long?>(null) }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    val selection = rememberSelection(servers.profiles.map { it.id })
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    val found = query?.let(servers::search)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = query != null && !selection.active,
        onBackCompleted = { query = null }
    )

    val startVpn = rememberVpnStartRequest(onGranted = viewModel::startVpn)
    val showMessage = rememberShowMessage()
    val localNetworkDenied = stringResource(Res.string.local_network_denied)
    val requestLocalNetwork = rememberLocalNetworkRequest { granted ->
        if (!granted) showMessage(localNetworkDenied)
        startVpn()
    }
    val onConnectClick = { if (ui.bypassLan) requestLocalNetwork() else startVpn() }

    val connected = state.isActive
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteLabel = stringResource(Res.string.delete)
    LaunchedEffect(profileViewModel) {
        profileViewModel.unreachable.collect { ids ->
            val message = getPluralString(Res.plurals.unreachable_servers, ids.size, ids.size)
            val result = snackbarHostState.showSnackbar(message, actionLabel = deleteLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                deleting = profileViewModel.uiState.value.profiles.filter { it.id in ids }.ifEmpty { null }
            }
        }
    }

    val bulk = ServerBulkActions(
        onSetHidden = profileViewModel::setHidden,
        onDelete = { ids -> deleting = servers.profiles.filter { it.id in ids } },
        onRemoveFromGroup = profileViewModel::removeFromGroup,
        onMove = { ids, done -> moving = MoveRequest(ids, done) },
        canMove = { ids ->
            servers.profiles.filter { it.id in ids }
                .let { picked -> picked.isNotEmpty() && picked.all { it.canJoinGroup(it.groupId?.let(servers.groups::get)) } }
        }
    )

    val content = ErkinScreenContent(
        ui = ui,
        state = state,
        stats = stats,
        servers = servers,
        query = query,
        shown = found ?: servers.tree.root,
        onQueryChange = { query = it },
        connected = connected,
        onToggle = { if (connected) viewModel.stopVpn() else onConnectClick() },
        onDismissHint = { viewModel.dismissHomeHint() },
        onNavigateToApps = onNavigateToApps,
        onByedpiClick = onNavigateToByeDpi,
        onAddServerClick = { adding = AddRequest(groupId = null) },
        onTest = profileViewModel::test,
        onReorder = profileViewModel::reorder,
        onSelect = { profileViewModel.select(it) },
        onEdit = { profile ->
            when (profile.kind) {
                is ServerKind.Link -> editing = profile
                is ServerKind.Chain -> editingChain = profile
            }
        },
        onShare = { profile -> sharer.shareText(profile.link, shareChooserTitle) },
        onDelete = { deleting = listOf(it) },
        groups = GroupActions(
            refreshing = servers.refreshing,
            onOpen = { sheetId = it },
            onTest = profileViewModel::test,
            onRefresh = profileViewModel::refreshSubscription,
            onAdd = { adding = AddRequest(groupId = it) },
            onRename = { renamingId = it },
            onShare = { id -> shareText(servers, id)?.let { sharer.shareText(it, shareChooserTitle) } },
            onUngroup = profileViewModel::dissolveGroup,
            onDelete = { id -> deleting = servers.profiles.filter { it.groupId == id } }
        ),
        onRemoveFromGroup = { profileViewModel.removeFromGroup(listOf(it.id)) },
        onEditLogin = profileViewModel::editLogin,
        snackbarHostState = snackbarHostState,
        selection = selection,
        bulk = bulk,
        onSetHidden = profileViewModel::setHidden
    )

    val windowSize = LocalWindowInfo.current.containerSize
    val landscape = windowSize.width > windowSize.height
    if (landscape) ErkinScreenLandscape(content) else ErkinScreenPortrait(content)

    sheetId?.let { id ->
        val item = servers.group(id)
        if (item == null) {
            LaunchedEffect(id) { sheetId = null }
        } else {
            ServerSheet(
                folder = ServerFolder.Group(id),
                choices = servers.choices,
                mode = ServerSheetMode.Manage(content.groups, bulk) { node, check, handle, close ->
                    FolderNodeRow(content, node, check, handle, sheet = item, onSelected = close)
                },
                onDismiss = { sheetId = null }
            )
        }
    }

    adding?.let { request ->
        AddServerDialog(
            onDismiss = { adding = null },
            onReadFile = { file, onRead -> profileViewModel.readConfigFile(file::open, onRead) },
            onAdd = { raw, fileName -> profileViewModel.add(raw, fileName, request.groupId); adding = null }
        )
    }

    moving?.let { request ->
        MoveToGroupDialog(
            groups = servers.tree.root.filterIsInstance<ServerNode.Group>().filter { it.kind == GroupKind.Local },
            onDismiss = { moving = null },
            onNewGroup = {
                profileViewModel.moveToNewGroup(request.ids)
                request.done()
                moving = null
            },
            onGroup = { groupId ->
                profileViewModel.moveToGroup(request.ids, groupId)
                request.done()
                moving = null
            }
        )
    }

    editingChain?.let { chain ->
        val chainInvalid = stringResource(Res.string.chain_invalid)
        ChainDialog(
            initial = chain,
            choices = chainChoices,
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
        val stored = servers.groups[id]
        if (stored == null) {
            LaunchedEffect(id) { renamingId = null }
        } else {
            RenameDialog(
                current = stored.name.orEmpty(),
                placeholder = stored.title ?: stored.url?.let(ServerGroupEntity::hostOf) ?: stored.name.orEmpty(),
                onDismiss = { renamingId = null },
                onSave = { name ->
                    profileViewModel.renameGroup(id, name)
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
        DestructiveConfirmDialog(
            onDismiss = { deleting = null },
            onConfirm = {
                profileViewModel.delete(profiles)
                selection.forget(profiles.map { it.id })
                deleting = null
            },
            title = {
                val title = if (profiles.size == 1) Res.string.delete_server_title else Res.string.delete_servers_title
                Text(stringResource(title))
            },
            text = {
                val last = profiles.size >= servers.profiles.size && ui.hasVpnApps
                val single = profiles.singleOrNull()
                if (single != null) {
                    val displayName = serverName(single.name)
                    val message = if (last) Res.string.delete_last_server_message else Res.string.delete_server_message
                    Text(stringResource(message, displayName))
                } else {
                    val message = if (last) Res.string.delete_all_servers_message else Res.string.delete_servers_message
                    val names = serverNames()
                    Column {
                        Text(stringResource(message, profiles.size))
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            profiles.forEach { profile ->
                                Text(
                                    text = "• ${names.of(profile.name)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    if (login != null) {
        val request = login
        LoginDialog(
            hint = stringResource(
                when {
                    request is LoginRequest.NewServer -> Res.string.login_openvpn_hint
                    request is LoginRequest.Existing && request.rejected -> Res.string.login_wrong
                    else -> Res.string.login_edit_hint
                }
            ),
            confirmLabel = stringResource(if (request is LoginRequest.NewServer) Res.string.add else Res.string.save),
            initialUsername = (request as? LoginRequest.Existing)?.username.orEmpty(),
            onDismiss = profileViewModel::cancelLogin,
            onSubmit = profileViewModel::submitLogin
        )
    }

    servers.error?.let { error ->
        val message = when (error) {
            ServerError.InvalidLink -> stringResource(Res.string.invalid_link_error)
            ServerError.EmptySubscription -> stringResource(Res.string.no_servers_in_subscription)
            is ServerError.FetchFailed -> stringResource(Res.string.fetch_subscription_failed, error.reason)
            is ServerError.Refused -> when (error.refusal) {
                SubscriptionRefusal.DEVICE_LIMIT -> stringResource(Res.string.subscription_device_limit)
                SubscriptionRefusal.HWID_REQUIRED -> stringResource(Res.string.subscription_hwid_required)
            }
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

private class AddRequest(val groupId: Long?)

private class MoveRequest(val ids: List<Long>, val done: () -> Unit)

private fun shareText(servers: ServersUiState, groupId: Long): String? = when (val kind = servers.groups[groupId]?.kind) {
    is GroupKind.Subscription -> kind.url
    GroupKind.Local -> servers.profiles.filter { it.groupId == groupId }.joinToString("\n") { it.link }
    null -> null
}

internal class ErkinScreenContent(
    val ui: ErkinUiState,
    val state: VpnState,
    val stats: ConnectionStats,
    val servers: ServersUiState,
    val query: String?,
    val shown: List<ServerNode>,
    val onQueryChange: (String?) -> Unit,
    val connected: Boolean,
    val onToggle: () -> Unit,
    val onDismissHint: () -> Unit,
    val onNavigateToApps: () -> Unit,
    val onByedpiClick: () -> Unit,
    val onAddServerClick: () -> Unit,
    val onTest: (servers: List<Long>) -> Deferred<List<Long>?>,
    val onReorder: (List<Long>) -> Unit,
    val onSelect: (ProfileEntity) -> Unit,
    val onEdit: (ProfileEntity) -> Unit,
    val onShare: (ProfileEntity) -> Unit,
    val onDelete: (ProfileEntity) -> Unit,
    val groups: GroupActions,
    val onRemoveFromGroup: (ProfileEntity) -> Unit,
    val onEditLogin: (ProfileEntity) -> Unit,
    val snackbarHostState: SnackbarHostState,
    val selection: Selection,
    val bulk: ServerBulkActions,
    val onSetHidden: (ids: Collection<Long>, hidden: Boolean) -> Unit
) {
    val searching: Boolean get() = query != null
}

@Composable
private fun ErkinScreenPortrait(c: ErkinScreenContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(KEYBOARD_INSETS)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        ConnectionSection(c)

        Spacer(Modifier.height(16.dp))
        ServerListPanel(
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
            .windowInsetsPadding(KEYBOARD_INSETS)
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
            ServerListPanel(
                c = c,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun ColumnScope.ConnectionSection(c: ErkinScreenContent, landscape: Boolean = false) {
    val hasServers = c.servers.profiles.isNotEmpty()
    if (!c.ui.homeHintSeen) {
        SetupChecklist(
            done = { step ->
                when (step) {
                    SetupStep.Server -> hasServers
                    SetupStep.Apps -> c.ui.hasByedpiApps || (hasServers && c.ui.hasVpnApps)
                    SetupStep.Connect -> c.connected
                }
            },
            onStep = { step ->
                when (step) {
                    SetupStep.Server -> c.onAddServerClick
                    SetupStep.Apps -> c.onNavigateToApps
                    SetupStep.Connect -> null
                }
            },
            onDismiss = c.onDismissHint
        )
        Spacer(Modifier.height(12.dp))
    }

    val canStart = c.ui.runsWithoutServer || (c.servers.selected != null && c.ui.hasRoutedApps)
    val enabled = c.state != VpnState.Connecting && (c.connected || canStart)

    if (landscape) {
        ConnectionCard(
            state = c.state,
            stats = c.stats,
            connected = c.connected,
            enabled = enabled,
            onToggle = c.onToggle,
            modifier = Modifier.weight(2f).fillMaxWidth().zIndex(1f)
        )
        ChannelsRow(c, modifier = Modifier.weight(1f).fillMaxWidth(), fillHeight = true)
    } else {
        ConnectionCard(
            state = c.state,
            stats = c.stats,
            connected = c.connected,
            enabled = enabled,
            onToggle = c.onToggle,
            modifier = Modifier.zIndex(1f)
        )
        ChannelsRow(c)
    }

    if (c.ui.homeHintSeen && !c.connected && !c.ui.hasRoutedApps) {
        Spacer(Modifier.height(12.dp))
        AddAppsCard(onClick = c.onNavigateToApps)
    }
}

@Composable
private fun ChannelsRow(c: ErkinScreenContent, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tuckedUnder(CONNECTION_CARD_CORNER)
            .then(if (fillHeight) Modifier else Modifier.height(IntrinsicSize.Min)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val cardModifier = Modifier.weight(1f).fillMaxHeight()
        ChannelStatusCard(
            type = ConnectionType.VPN,
            active = c.connected,
            up = c.stats.vpnUp,
            hasApps = c.ui.hasVpnApps,
            noServers = c.servers.profiles.isEmpty(),
            latencyMs = c.stats.vpnLatencyMs,
            country = c.stats.vpnCountry,
            shape = RoundedCornerShape(bottomStart = CONNECTION_CARD_CORNER),
            topInset = CONNECTION_CARD_CORNER,
            modifier = cardModifier
        )
        ChannelStatusCard(
            type = ConnectionType.BYEDPI,
            active = c.stats.byedpiActive,
            up = c.stats.byedpiUp,
            hasApps = c.ui.hasByedpiApps,
            shape = RoundedCornerShape(bottomEnd = CONNECTION_CARD_CORNER),
            topInset = CONNECTION_CARD_CORNER,
            modifier = cardModifier,
            reachable = c.stats.byedpiReachable,
            total = c.stats.byedpiTotal,
            checking = c.stats.byedpiChecking,
            onClick = c.onByedpiClick
        )
    }
}

private fun Modifier.tuckedUnder(depth: Dp): Modifier = layout { measurable, constraints ->
    val shift = depth.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = constraints.minHeight + shift,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + shift else Constraints.Infinity
        )
    )
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
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

private val KEYBOARD_INSETS: WindowInsets
    @Composable get() = WindowInsets.ime.exclude(WindowInsets.navigationBars)
