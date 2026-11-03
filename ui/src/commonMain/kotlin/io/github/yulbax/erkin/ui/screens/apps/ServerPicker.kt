package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.AppSearchBar
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import io.github.yulbax.erkin.ui.viewmodel.ServerPickerRow
import io.github.yulbax.erkin.ui.viewmodel.serverPickerRows
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerPickerSheet(
    subtitle: String,
    choices: ServerChoices,
    current: CurrentServer,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()
    val pick: (Long?) -> Unit = { id ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            onPick(id)
        }
    }
    var query by remember { mutableStateOf("") }
    var expanded by remember {
        mutableStateOf(setOfNotNull((current as? CurrentServer.Bound)?.let { choices.subscriptionOf(it.serverId) }))
    }
    val rows = serverPickerRows(choices, query, expanded, current)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            Text(stringResource(Res.string.apps_server_title), style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.apps_server_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ServerPickerRow.Main -> ServerRow(
                        name = stringResource(Res.string.app_server_main, row.name),
                        checked = row.checked,
                        onClick = { pick(null) }
                    )
                    is ServerPickerRow.Subscription -> SubscriptionHeader(row.name, row.count, row.open) {
                        expanded = if (row.open) expanded - row.id else expanded + row.id
                    }
                    is ServerPickerRow.Server -> ServerRow(
                        name = row.name,
                        checked = row.checked,
                        indented = row.indented,
                        onClick = { pick(row.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionHeader(name: String, count: Int, open: Boolean, onClick: () -> Unit) {
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

@Composable
private fun ServerRow(name: String, checked: Boolean, indented: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = if (indented) 24.dp else 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (checked) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
