package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.components.ChoiceRow
import io.github.yulbax.erkin.ui.components.serverName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.PickerSheet
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.ui.viewmodel.ServerPickerRow
import io.github.yulbax.erkin.ui.viewmodel.serverPickerRows
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ServerPickerSheet(
    subtitle: String,
    choices: ServerChoices,
    current: CurrentServer,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember {
        mutableStateOf(setOfNotNull((current as? CurrentServer.Bound)?.let { choices.groupOf(it.serverId) }))
    }
    val rows = serverPickerRows(choices, query, expanded, current)

    PickerSheet(
        title = stringResource(Res.string.apps_server_title),
        subtitle = subtitle,
        searchPlaceholder = stringResource(Res.string.apps_server_search),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss
    ) { close ->
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ServerPickerRow.Main -> ChoiceRow(
                    title = stringResource(Res.string.app_server_main, serverName(row.name)),
                    checked = row.checked,
                    onClick = { close { onPick(null) } }
                )
                is ServerPickerRow.Group -> GroupHeader(row.name, row.count, row.open) {
                    expanded = if (row.open) expanded - row.id else expanded + row.id
                }
                is ServerPickerRow.Server -> ChoiceRow(
                    title = serverName(row.name),
                    checked = row.checked,
                    onClick = { close { onPick(row.id) } },
                    padding = PaddingValues(start = if (row.indented) 24.dp else 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(Res.plurals.subscription_servers, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

