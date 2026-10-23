package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Apps(
    query: String,
    viewModel: AppsViewModel = koinViewModel()
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val appsHintSeen by viewModel.appsHintSeen.collectAsStateWithLifecycle()
    val hasServers by viewModel.hasServers.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    when {
        isLoading -> LoadingContent()
        error != null -> ErrorContent(error = error ?: "", onRetry = viewModel::retry)
        else -> {
            val filtered = remember(apps, query) {
                if (query.isBlank()) apps
                else apps.filter { app ->
                    app.name.contains(query, ignoreCase = true) ||
                            app.packageName.contains(query, ignoreCase = true)
                }
            }
            val bulkTargets = if (query.isBlank()) allApps else filtered

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!appsHintSeen) AppsHint(onDismiss = viewModel::dismissAppsHint)
                    val snackbarPattern = pluralStringResource(
                        Res.plurals.set_apps_snackbar,
                        bulkTargets.size
                    )
                    val undoLabel = stringResource(Res.string.undo)
                    val typeLabels = ConnectionType.entries.associateWith { it.label() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.apply_to_all),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        GlobalTypeConnectedGroup(
                            vpnEnabled = hasServers,
                            modifier = Modifier.weight(1f),
                            onSelect = { type ->
                                val snackMsg = snackbarPattern.format(bulkTargets.size, typeLabels.getValue(type))
                                viewModel.setAllConnectionTypes(bulkTargets, type)
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = snackMsg,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreConnectionTypes(bulkTargets)
                                    }
                                }
                            }
                        )
                    }
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()) {
                        when {
                            query.isNotBlank() && filtered.isEmpty() -> NoResultsContent(query = query)
                            filtered.isEmpty() -> EmptyContent()
                            else -> AppsList(
                                apps = filtered,
                                vpnEnabled = hasServers,
                                servers = servers,
                                onConnectionTypeChange = viewModel::setConnectionType,
                                onServerChange = viewModel::setServer
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
        }
    }
}
