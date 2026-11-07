package io.github.yulbax.erkin.ui.screens.byedpi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.byedpi.SiteList
import io.github.yulbax.erkin.data.byedpi.SiteListDraft
import io.github.yulbax.erkin.data.byedpi.canDisable
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.SiteListsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface SiteListDialog {
    data object Add : SiteListDialog
    data class Edit(val id: Long) : SiteListDialog
}

@Composable
fun SiteLists(viewModel: SiteListsViewModel = koinViewModel()) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SiteListDialog?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(Res.string.byedpi_site_lists_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        GroupCard(
            items = lists.map { list ->
                { SiteListRow(list, canToggle = lists.canDisable(list), viewModel::setActive) { dialog = SiteListDialog.Edit(list.id) } }
            }
        )
        FilledTonalButton(
            onClick = { dialog = SiteListDialog.Add },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.byedpi_site_list_add))
        }
    }

    when (val current = dialog) {
        SiteListDialog.Add -> SiteListEditor(
            title = stringResource(Res.string.byedpi_site_list_add),
            initial = SiteListDraft("", ""),
            onDismiss = { dialog = null },
            onSave = { viewModel.add(it); dialog = null }
        )
        is SiteListDialog.Edit -> lists.firstOrNull { it.id == current.id }?.let { list ->
            SiteListEditor(
                title = stringResource(Res.string.byedpi_site_list_edit),
                initial = SiteListDraft.of(list),
                onDismiss = { dialog = null },
                onSave = { viewModel.edit(list.id, it); dialog = null },
                onReset = if (list.resettable) ({ viewModel.reset(list.id); dialog = null }) else null,
                onDelete = if (list.deletable && lists.canDisable(list)) ({ viewModel.delete(list.id); dialog = null }) else null
            )
        }
        null -> Unit
    }
}

@Composable
private fun SiteListRow(list: SiteList, canToggle: Boolean, onToggle: (Long, Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(list.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = pluralStringResource(Res.plurals.byedpi_site_count, list.sites.size, list.sites.size)
            val modified = stringResource(Res.string.byedpi_site_list_modified)
            Text(
                if (list.resettable) "$count · $modified" else count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = list.active, enabled = canToggle, onCheckedChange = { onToggle(list.id, it) })
    }
}

@Composable
private fun SiteListEditor(
    title: String,
    initial: SiteListDraft,
    onDismiss: () -> Unit,
    onSave: (SiteListDraft) -> Unit,
    onReset: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial.name) }
    var text by remember { mutableStateOf(initial.text) }
    val draft = remember(name, text) { SiteListDraft(name, text) }
    AppDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.delete)) }
            }
            if (onReset != null) {
                TextButton(onClick = onReset) { Text(stringResource(Res.string.byedpi_site_list_reset)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onSave(draft) }, enabled = draft.valid) { Text(stringResource(Res.string.save)) }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf(
                {
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(SiteListDraft.MAX_NAME_LENGTH) },
                        label = { Text(stringResource(Res.string.name_label)) },
                        singleLine = true,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(Res.string.byedpi_site_list_sites)) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    )
                }
            )
        )
    }
}
