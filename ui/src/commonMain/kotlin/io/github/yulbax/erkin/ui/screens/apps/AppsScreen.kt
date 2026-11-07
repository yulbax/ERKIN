package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.platform.platformChannels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.AppInfo
import io.github.yulbax.erkin.ui.viewmodel.boundServers
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.ui.viewmodel.AppFilter
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.viewmodel.AppsMode
import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed interface ServerPickTarget {
    data class App(val app: AppInfo) : ServerPickTarget
    data object Selection : ServerPickTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.servers_selected, count), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
            }
            actions()
        }
    )
}

@Composable
fun Apps(viewModel: AppsViewModel = koinViewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val visible by viewModel.visibleApps.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val appsHintSeen by viewModel.appsHintSeen.collectAsStateWithLifecycle()
    val choices by viewModel.serverChoices.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<ServerPickTarget?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedCount = (mode as? AppsMode.Selecting)?.packages?.size ?: 0

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = mode is AppsMode.Selecting,
        onBackCompleted = viewModel::stopSelecting
    )

    when {
        isLoading -> LoadingContent()
        error != null -> ErrorContent(error = error ?: "", onRetry = viewModel::retry)
        else -> {
            val snackbarPattern = pluralStringResource(Res.plurals.set_apps_snackbar, selectedCount)
            val undoLabel = stringResource(Res.string.undo)
            val typeLabels = ConnectionType.entries.associateWith { it.label() }
            val offerUndo: (ConnectionType, List<AppInfo>) -> Unit = { type, snapshot ->
                val message = snackbarPattern.format(snapshot.size, typeLabels.getValue(type))
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreConnectionTypes(snapshot)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!appsHintSeen) AppsHint(onDismiss = viewModel::dismissAppsHint)
                    when (val current = mode) {
                        is AppsMode.Selecting -> BulkChannelBar(
                            enabled = selectedCount > 0,
                            blockEnabled = current.canBlock(apps.size),
                            vpnEnabled = choices.vpnEnabled,
                            canChooseServer = choices.canChoose,
                            onSelect = { type -> offerUndo(type, viewModel.setConnectionTypeForSelection(type)) },
                            onChooseServer = { picking = ServerPickTarget.Selection },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                        AppsMode.Browsing -> ChannelFilter(apps = apps, choices = choices, selected = filter, onSelect = viewModel::selectFilter)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            visible.isEmpty() && viewModel.query.isNotBlank() -> NoResultsContent(query = viewModel.query)
                            visible.isEmpty() -> EmptyContent()
                            else -> AppsList(
                                apps = visible,
                                mode = mode,
                                choices = choices,
                                onConnectionTypeChange = viewModel::setConnectionType,
                                onChooseServer = { app -> picking = ServerPickTarget.App(app) },
                                onToggle = viewModel::toggleSelected,
                                onStartSelecting = viewModel::startSelecting
                            )
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when (val target = picking) {
                null -> Unit
                is ServerPickTarget.App -> ServerPickerSheet(
                    subtitle = target.app.name,
                    choices = choices,
                    current = choices.current(target.app),
                    onPick = { serverId -> viewModel.setServer(target.app, serverId) },
                    onDismiss = { picking = null }
                )
                ServerPickTarget.Selection -> ServerPickerSheet(
                    subtitle = stringResource(Res.string.servers_selected, selectedCount),
                    choices = choices,
                    current = CurrentServer.None,
                    onPick = { serverId -> offerUndo(ConnectionType.VPN, viewModel.setServerForSelection(serverId)) },
                    onDismiss = { picking = null }
                )
            }
        }
    }
}

@Composable
private fun ChannelFilter(apps: List<AppInfo>, choices: ServerChoices, selected: AppFilter?, onSelect: (AppFilter?) -> Unit) {
    val counts = apps.groupingBy { it.connectionType }.eachCount()
    val servers = choices.boundServers(apps).let { bound ->
        val chosen = (selected as? AppFilter.Server)?.serverId
        if (chosen == null || bound.any { it.first == chosen }) bound else bound + (chosen to 0)
    }
    val toggle = { filter: AppFilter -> onSelect(if (selected == filter) null else filter) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("${stringResource(Res.string.apps_filter_all)} ${apps.size}") }
        )
        platformChannels.forEach { type ->
            val filter = AppFilter.Channel(type)
            FilterChip(
                selected = selected == filter,
                onClick = { toggle(filter) },
                label = { Text("${type.label()} ${counts[type] ?: 0}") },
                leadingIcon = { ConnectionTypeIcon(type, Modifier.size(16.dp)) }
            )
        }
        servers.forEach { (serverId, count) ->
            val server = choices.label(ConnectionType.VPN, serverId) ?: return@forEach
            val filter = AppFilter.Server(serverId)
            FilterChip(
                selected = selected == filter,
                onClick = { toggle(filter) },
                label = { Text("${server.name} $count", maxLines = 1) },
                leadingIcon = {
                    CompositionLocalProvider(LocalContentColor provides if (server.unreachable) warningColor() else LocalContentColor.current) {
                        SourceIcon(server.icon(ConnectionType.VPN), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            )
        }
    }
}
