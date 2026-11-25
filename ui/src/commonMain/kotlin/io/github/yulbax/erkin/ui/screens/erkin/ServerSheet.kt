package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.ui.components.serverName
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.vpn.ServerChecks
import org.koin.compose.koinInject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.PickerSheet
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import io.github.yulbax.erkin.ui.viewmodel.PickerLead
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.ui.viewmodel.ServerPickerRow
import io.github.yulbax.erkin.data.profile.ServerFolder
import io.github.yulbax.erkin.ui.viewmodel.serverPickerPage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.yulbax.erkin.data.profile.matching
import io.github.yulbax.erkin.ui.components.PickerList
import io.github.yulbax.erkin.ui.components.rememberSelection

internal sealed interface ServerSheetMode {
    class Pick(
        val current: CurrentServer,
        val onPick: (Long?) -> Unit,
        val lead: PickerLead = PickerLead.MAIN_SERVER
    ) : ServerSheetMode

    class Manage(
        val actions: GroupActions,
        val servers: ServerBulkActions,
        val row: @Composable (node: ServerNode, check: RowCheck, handle: @Composable () -> Unit, close: () -> Unit) -> Unit
    ) : ServerSheetMode
}

@Composable
internal fun ServerSheet(
    folder: ServerFolder,
    choices: ServerChoices,
    mode: ServerSheetMode,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
    when (mode) {
        is ServerSheetMode.Pick -> PickSheet(folder, choices, mode, onDismiss, subtitle)
        is ServerSheetMode.Manage -> ManageSheet(folder, choices, mode, onDismiss)
    }
}

@Composable
private fun PickSheet(folder: ServerFolder, choices: ServerChoices, mode: ServerSheetMode.Pick, onDismiss: () -> Unit, subtitle: String?) {
    var query by remember { mutableStateOf("") }
    var browsed by remember {
        mutableStateOf(
            (mode.current as? CurrentServer.Bound)?.let { choices.groupOf(it.serverId) }?.let(ServerFolder::Group) ?: folder
        )
    }
    val tests = koinInject<ServerChecks>()
    val testing by tests.testing.collectAsState()
    val page = serverPickerPage(choices, query, browsed, mode.current, mode.lead, testing)

    PickerSheet(
        title = page.group ?: stringResource(Res.string.apps_server_title),
        subtitle = subtitle,
        searchPlaceholder = stringResource(Res.string.search_servers),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss,
        page = page,
        pageKey = { it.folder },
        onBack = if (page.group != null && page.folder != folder) ({ browsed = folder }) else null,
        action = page.testQueue.takeIf { it.isNotEmpty() }?.let { queue ->
            { TestButton(testing = testing.isNotEmpty()) { tests.run(queue) } }
        }
    ) { shown, close ->
        PickerList(state = rememberLazyListState(initialFirstVisibleItemIndex = shown.firstShownRow)) {
            items(shown.rows, key = { it.key }) { row ->
                when (row) {
                    is ServerPickerRow.Main -> LeadChoice(stringResource(Res.string.app_server_main, serverName(row.name)), row.checked) {
                        close { mode.onPick(null) }
                    }
                    is ServerPickerRow.ByeDpi -> LeadChoice(stringResource(Res.string.chain_byedpi), row.checked) {
                        close { mode.onPick(null) }
                    }
                    is ServerPickerRow.Group -> GroupChoice(row) { browsed = ServerFolder.Group(row.id) }
                    is ServerPickerRow.Server -> ServerRowLayout(
                        title = serverName(row.name),
                        subtitle = row.group.orEmpty(),
                        details = row.protocol,
                        isSelected = row.checked,
                        mode = RowMode.Normal,
                        result = row.result,
                        testing = row.testing,
                        isBest = false,
                        onClick = { close { mode.onPick(row.id) } },
                        onLongClick = {},
                        menu = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageSheet(folder: ServerFolder, choices: ServerChoices, mode: ServerSheetMode.Manage, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val tree = choices.tree
    val group = (folder as? ServerFolder.Group)?.let { tree.group(it.id) }
    val nodes = if (query.isBlank()) tree.children(folder) else tree.search(folder, query)
    val hidden = tree.hidden(folder).matching(query)
    val queue = tree.testQueue(nodes.flatMap { it.profileIds })
    val testing by koinInject<ServerChecks>().testing.collectAsState()
    val listState = rememberLazyListState()
    val scrollAfterSort = rememberScrollToTopAfterSort(listState, nodes.flatMap { it.profileIds })

    PickerSheet(
        title = group?.name.orEmpty(),
        subtitle = group?.let { groupSummary(it) },
        searchPlaceholder = stringResource(Res.string.search_servers),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss,
        page = folder,
        pageKey = { it },
        onBack = null,
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                group?.let { GroupAddOrRefresh(it, mode.actions) }
                if (queue.isNotEmpty()) TestButton(testing = testing.isNotEmpty()) { scrollAfterSort(mode.actions.onTest(queue)) }
                group?.let { GroupMenuButton(it, mode.actions) }
            }
        },
        extra = group?.takeIf { it.kind is GroupKind.Subscription }?.let { subscription -> { SubscriptionCard(subscription, mode.actions) } }
    ) { shown, close ->
        val selection = rememberSelection((group?.members.orEmpty() + group?.hidden.orEmpty()).map { it.id })
        FolderList(
            nodes = nodes,
            kind = FolderKind.of(shown, tree),
            selection = selection,
            bulk = mode.servers,
            row = { node, check, handle -> mode.row(node, check, handle) { close {} } },
            hidden = hidden,
            visibleInFolder = group?.profileIds.orEmpty().toSet(),
            listState = listState,
            sidePadding = 12.dp
        )
    }
}

@Composable
private fun groupSummary(group: ServerNode.Group): String =
    listOfNotNull(
        pluralStringResource(Res.plurals.subscription_servers, group.members.size, group.members.size),
        group.updatedAt?.takeIf { group.kind !is GroupKind.Subscription }?.let { stringResource(Res.string.subscription_updated, updatedText(it)) }
    ).joinToString(" · ")

@Composable
private fun GroupAddOrRefresh(group: ServerNode.Group, actions: GroupActions) {
    when (group.kind) {
        is GroupKind.Subscription -> RefreshButton(refreshing = group.id in actions.refreshing) { actions.onRefresh(group.id) }
        GroupKind.Local -> IconButton(onClick = { actions.onAdd(group.id) }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.add_server))
        }
    }
}

@Composable
private fun TestButton(testing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !testing) {
        PingIcon(testing = testing, contentDescription = stringResource(Res.string.server_test_latency))
    }
}

@Composable
private fun GroupChoice(row: ServerPickerRow.Group, onOpen: () -> Unit) {
    ServerRowLayout(
        title = row.name,
        subtitle = row.current?.let { serverName(it) }.orEmpty(),
        details = pluralStringResource(Res.plurals.subscription_servers, row.count, row.count),
        isSelected = row.holdsCurrent,
        mode = RowMode.Normal,
        result = null,
        testing = false,
        isBest = false,
        onClick = onOpen,
        onLongClick = {},
        titleIcon = row.kind.rowIcon,
        menu = {
            FilledRowButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.subscription_show_servers))
            }
        }
    )
}

@Composable
private fun LeadChoice(title: String, checked: Boolean, onClick: () -> Unit) {
    ServerRowLayout(
        title = title,
        subtitle = "",
        isSelected = checked,
        mode = RowMode.Normal,
        result = null,
        testing = false,
        isBest = false,
        onClick = onClick,
        onLongClick = {},
        menu = {}
    )
}
