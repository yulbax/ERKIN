package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.ui.components.AppSearchBar
import io.github.yulbax.erkin.ui.components.EmptyAction
import io.github.yulbax.erkin.ui.components.EmptyState
import io.github.yulbax.erkin.ui.components.FloatingVisibility
import io.github.yulbax.erkin.ui.components.SwipeableSnackbarHost
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.screens.chains.chainLabel
import org.jetbrains.compose.resources.stringResource
import io.github.yulbax.erkin.ui.components.LabeledFrame

@Composable
internal fun ServerListPanel(c: ErkinScreenContent, modifier: Modifier) {
    if (c.servers.profiles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                text = stringResource(Res.string.servers_empty),
                action = EmptyAction(stringResource(Res.string.add_server), c.onAddServerClick)
            )
        }
        return
    }
    val label = if (c.filtering) {
        stringResource(Res.string.servers_found_frame, c.shown.size)
    } else {
        stringResource(Res.string.servers_frame, c.servers.profiles.count { !it.hidden })
    }
    LabeledFrame(label = label, modifier = modifier) {
        val listState = rememberLazyListState()
        val scrollAfterSort = rememberScrollToTopAfterSort(listState, c.servers.profiles.map { it.id })
        FolderList(
            nodes = c.shown,
            kind = FolderKind.ROOT,
            selection = c.selection,
            bulk = c.bulk,
            row = { node, check, handle -> FolderNodeRow(c, node, check, handle) },
            modifier = Modifier.fillMaxSize(),
            listState = listState,
            onReorder = c.onReorder.takeIf { c.servers.manualOrder && !c.filtering },
            emptyText = if (c.filtering) stringResource(Res.string.servers_search_empty) else null,
            sidePadding = 6.dp,
            topPadding = 6.dp,
            keepBarRoom = true
        ) {
            val notifying = c.snackbarHostState.currentSnackbarData != null
            ServerToolbar(
                c = c,
                visible = !c.selection.active && !notifying,
                onTest = { scrollAfterSort(c.onTest(c.shown.flatMap { it.profileIds })) }
            )
            SwipeableSnackbarHost(c.snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
internal fun FolderNodeRow(
    c: ErkinScreenContent,
    node: ServerNode,
    check: RowCheck,
    handle: @Composable () -> Unit,
    sheet: ServerNode.Group? = null,
    onSelected: () -> Unit = {}
) {
    when (node) {
        is ServerNode.Server -> ServerListRow(c, node, check.modeWith(handle), check, sheet, onSelected)
        is ServerNode.Group -> GroupListRow(c, node, check.modeWith(handle), check)
    }
}

@Composable
private fun BoxScope.ServerToolbar(c: ErkinScreenContent, visible: Boolean, onTest: () -> Unit) {
    FloatingVisibility(
        visible = visible && !c.searching,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = TOOLBAR_SHADOW
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { c.onQueryChange("") }, modifier = Modifier.size(TOOLBAR_BUTTON)) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_servers))
                }
                FilledRowButton(onClick = onTest, enabled = !c.servers.testing) {
                    PingIcon(testing = c.servers.testing, contentDescription = stringResource(Res.string.server_test_latency))
                }
                IconButton(onClick = c.onAddServerClick, modifier = Modifier.size(TOOLBAR_BUTTON)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_server))
                }
            }
        }
    }
    FloatingVisibility(
        visible = visible && c.searching,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        ServerSearchField(query = c.query.orEmpty(), onQueryChange = c.onQueryChange)
    }
}

@Composable
private fun ServerSearchField(query: String, onQueryChange: (String?) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = TOOLBAR_SHADOW
    ) {
        AppSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.search_servers),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(focus),
            trailing = {
                if (query.isEmpty()) {
                    IconButton(onClick = { onQueryChange(null) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                    }
                }
            }
        )
    }
}

private val TOOLBAR_SHADOW = 3.dp
private val TOOLBAR_BUTTON = 40.dp
private const val HIDDEN_ALPHA = 0.6f

@Composable
private fun ServerListRow(
    c: ErkinScreenContent,
    item: ServerNode.Server,
    mode: RowMode,
    check: RowCheck,
    sheet: ServerNode.Group?,
    onSelected: () -> Unit
) {
    val profile = item.profile
    val group = item.group
    val chainLabel = chainLabel(profile, c.servers.profiles)
    val groupServers = profile.groupId?.let { id -> c.servers.profiles.filter { it.groupId == id } }.orEmpty()
    val menuEnd = ServerMenuEnd.of(profile, profile.groupId?.let(c.servers.groups::get), groupServers)
    ServerRow(
        profile = profile,
        isSelected = profile.id == c.servers.selected?.id,
        actions = ServerActions(
            onEdit = { c.onEdit(profile) },
            onShare = if (chainLabel == null) ({ c.onShare(profile) }) else null,
            onRefreshSubscription = group?.takeIf { it.kind is GroupKind.Subscription }?.let { { c.groups.onRefresh(it.id) } },
            onRemoveFromGroup = sheet?.takeIf { it.kind == GroupKind.Local }?.let { { c.onRemoveFromGroup(profile) } },
            onEditLogin = if (profile.protocol == ProxyProtocol.OPENVPN) ({ c.onEditLogin(profile) }) else null,
            onMenuEnd = {
                when (menuEnd) {
                    ServerMenuEnd.DELETE -> c.onDelete(profile)
                    ServerMenuEnd.HIDE -> c.onSetHidden(listOf(profile.id), true)
                    ServerMenuEnd.SHOW -> c.onSetHidden(listOf(profile.id), false)
                }
            },
            menuEnd = menuEnd,
            menuEndEnabled = menuEnd.allowedFor(profile, groupServers)
        ),
        onSelect = {
            when {
                check.selecting -> check.onToggle()
                profile.hidden -> Unit
                else -> {
                    c.onSelect(profile)
                    onSelected()
                }
            }
        },
        onLongClick = check.onToggle,
        modifier = if (profile.hidden) Modifier.alpha(HIDDEN_ALPHA) else Modifier,
        result = c.servers.results[profile.id],
        testing = profile.id in c.servers.testingIds,
        isBest = profile.id == c.servers.fastest,
        chainLabel = chainLabel,
        groupIcon = group?.kind?.rowIcon,
        groupName = group?.displayName?.takeIf { c.filtering },
        mode = mode
    )
}

@Composable
private fun GroupListRow(c: ErkinScreenContent, item: ServerNode.Group, mode: RowMode, check: RowCheck) {
    val current = item.current
    GroupRow(
        item = item,
        actions = c.groups,
        isSelected = current.id == c.servers.selected?.id,
        result = c.servers.results[current.id],
        testing = current.id in c.servers.testingIds,
        isBest = current.id == c.servers.fastest,
        mode = mode,
        onClick = { if (check.selecting) check.onToggle() else c.onSelect(current) },
        onLongClick = check.onToggle
    )
}
