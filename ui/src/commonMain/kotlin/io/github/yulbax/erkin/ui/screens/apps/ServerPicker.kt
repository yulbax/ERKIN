package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.components.ChoiceRow
import io.github.yulbax.erkin.ui.components.color
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.components.serverName
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.Modifier
import io.github.yulbax.erkin.ui.screens.erkin.rowIcon
import io.github.yulbax.erkin.ui.screens.erkin.WaveDots
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.filled.Speed
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import io.github.yulbax.erkin.vpn.ServerChecks
import org.koin.compose.koinInject
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.yulbax.erkin.ui.viewmodel.ServerPickerRow
import io.github.yulbax.erkin.ui.viewmodel.serverPickerPage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerPickerSheet(
    subtitle: String,
    choices: ServerChoices,
    current: CurrentServer,
    lead: PickerLead = PickerLead.MAIN_SERVER,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var openGroup by remember {
        mutableStateOf((current as? CurrentServer.Bound)?.let { choices.groupOf(it.serverId) })
    }
    val tests = koinInject<ServerChecks>()
    val testing by tests.testing.collectAsState()
    val page = serverPickerPage(choices, query, openGroup, current, lead, testing)

    PickerSheet(
        title = page.group ?: stringResource(Res.string.apps_server_title),
        subtitle = subtitle,
        searchPlaceholder = stringResource(Res.string.apps_server_search),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss,
        page = page,
        pageKey = { it.openGroup },
        onBack = if (page.group != null) ({ openGroup = null }) else null,
        action = page.testQueue.takeIf { it.isNotEmpty() }?.let { queue ->
            { TestButton(testing = testing.isNotEmpty()) { tests.run(queue) } }
        }
    ) { shown, close ->
        items(shown.rows, key = { it.key }) { row ->
            when (row) {
                is ServerPickerRow.Main -> ChoiceRow(
                    title = stringResource(Res.string.app_server_main, serverName(row.name)),
                    checked = row.checked,
                    onClick = { close { onPick(null) } }
                )
                is ServerPickerRow.ByeDpi -> ChoiceRow(
                    title = stringResource(Res.string.chain_byedpi),
                    checked = row.checked,
                    onClick = { close { onPick(null) } }
                )
                is ServerPickerRow.Group -> GroupChoice(row) { openGroup = row.id }
                is ServerPickerRow.Server -> ChoiceRow(
                    title = serverName(row.name),
                    subtitle = row.group,
                    checked = row.checked,
                    onClick = { close { onPick(row.id) } },
                    trailing = when {
                        row.testing -> ({ WaveDots(color = MaterialTheme.colorScheme.onSurfaceVariant) })
                        else -> row.result?.let { result ->
                            { Text(result.label(), style = MaterialTheme.typography.bodySmall, color = result.color()) }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TestButton(testing: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !testing) {
        if (testing) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        } else {
            Icon(Icons.Default.Speed, contentDescription = stringResource(Res.string.server_test_latency))
        }
    }
}

@Composable
private fun GroupChoice(row: ServerPickerRow.Group, onOpen: () -> Unit) {
    val count = pluralStringResource(Res.plurals.subscription_servers, row.count, row.count)
    ChoiceRow(
        title = row.name,
        subtitle = row.current?.let { "$count · ${serverName(it)}" } ?: count,
        checked = false,
        subtitleIcon = row.kind.rowIcon.source,
        onClick = onOpen,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
