package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.rulelist.RuleListError
import io.github.yulbax.erkin.rulelist.RuleListException
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.RuleSetsViewModel
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import io.github.yulbax.erkin.vpn.core.RuleValues
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RuleSets(viewModel: RuleSetsViewModel = koinViewModel()) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val showMessage = rememberShowMessage()
    var error by remember { mutableStateOf<RuleListError?>(null) }
    var draft by remember { mutableStateOf<RoutingRuleEntity?>(null) }
    var addingLink by remember { mutableStateOf(false) }
    var pickingCountry by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    val report = { failure: Throwable? -> error = (failure as? RuleListException)?.let { RuleListError(it.failure, it.detail) } }
    val download = { source: RuleListSource, target: RuleTarget, name: String? ->
        viewModel.add(source, name) { failure ->
            report(failure)
            if (failure == null) {
                draft = RoutingRuleEntity(type = RuleMatch.RULE_SET.wire, value = source.url, target = target.wire)
            }
        }
    }
    error?.let { current ->
        val message = current.text()
        LaunchedEffect(current) {
            showMessage(message)
            error = null
        }
    }

    val library = states.entries.map { (url, state) -> Triple(url, ruleSetName(url, state), state) }
        .sortedBy { it.second.lowercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item(key = MINE_KEY) {
            GroupCard(
                title = stringResource(Res.string.rule_sets_mine),
                items = library.map<Triple<String, String, RuleListState>, @Composable () -> Unit> { (url, name, state) ->
                    {
                        LibraryRow(
                            name = name,
                            state = state,
                            onClick = { renaming = url },
                            onUpdate = { viewModel.update(url, report) },
                            onDelete = { deleting = url }
                        )
                    }
                } + listOf<@Composable () -> Unit> {
                    ActionRow(Icons.Default.Add, stringResource(Res.string.rule_sets_add_link)) { addingLink = true }
                },
                description = stringResource(Res.string.rule_sets_empty).takeIf { library.isEmpty() }
            )
        }
        items(RuleSetGroup.entries, key = { it.name }) { group ->
            GroupCard(
                title = stringResource(group.label),
                items = RuleSetPreset.entries.filter { it.group == group }.map<RuleSetPreset, @Composable () -> Unit> { preset ->
                    {
                        PresetRow(
                            name = stringResource(preset.label),
                            detail = linkHost(preset.url),
                            state = states[preset.url],
                            onDownload = { download(preset.source, preset.target, null) }
                        )
                    }
                }
            )
        }
        item(key = COUNTRIES_KEY) {
            GroupCard(
                title = stringResource(Res.string.rule_sets_countries),
                description = stringResource(Res.string.rule_sets_countries_description),
                items = listOf {
                    ActionRow(Icons.Default.Public, stringResource(Res.string.rule_sets_pick_country)) { pickingCountry = true }
                }
            )
        }
    }

    if (addingLink) {
        AddLinkDialog(
            normalize = viewModel::normalizeLink,
            onDismiss = { addingLink = false },
            onAdd = { url, name ->
                addingLink = false
                download(RuleListSource(url, null), RuleTarget.DIRECT, name)
            }
        )
    }

    if (pickingCountry) {
        CountryDialog(
            onDismiss = { pickingCountry = false },
            onPick = { code ->
                pickingCountry = false
                download(RuleLists.country(code), RuleTarget.DIRECT, null)
            }
        )
    }

    renaming?.let { url ->
        RenameDialog(
            current = states[url]?.name.orEmpty(),
            placeholder = ruleSetName(url, states[url]?.copy(name = null)),
            onDismiss = { renaming = null },
            onSave = { name ->
                viewModel.rename(url, name)
                renaming = null
            }
        )
    }

    deleting?.let { url ->
        val used = usage[url] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(url)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.cancel)) } },
            title = { Text(stringResource(Res.string.rule_set_delete_title)) },
            text = {
                val message = stringResource(Res.string.rule_set_delete_message, ruleSetName(url, states[url]))
                Text(if (used > 0) message + "\n\n" + stringResource(Res.string.rule_set_delete_rules, used) else message)
            }
        )
    }

    draft?.let { rule ->
        RuleDialog(
            initial = rule,
            ruleSets = states,
            onDismiss = { draft = null },
            onSave = { match, raw, target ->
                viewModel.createRule(match, raw, target)
                draft = null
            },
            onDelete = null,
            onOpenRuleSets = { draft = null },
            isNew = true
        )
    }
}

@Composable
private fun LibraryRow(
    name: String,
    state: RuleListState,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(name, state.text(), state.textColor(), Modifier.weight(1f))
        if (state.downloading) {
            Progress()
        } else {
            IconButton(onClick = onUpdate) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.rule_list_update))
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
        }
    }
}

@Composable
private fun PresetRow(name: String, detail: String, state: RuleListState?, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(
            title = name,
            subtitle = state?.text() ?: detail,
            subtitleColor = state?.textColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        when {
            state?.downloading == true -> Progress()
            state?.installed != null -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
            else -> IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = stringResource(Res.string.rule_set_download))
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RowText(title: String, subtitle: String, subtitleColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Progress() {
    CircularProgressIndicator(modifier = Modifier.padding(14.dp).size(20.dp), strokeWidth = 2.dp)
}

@Composable
private fun AddLinkDialog(normalize: (String) -> String?, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val url = normalize(link)
    AppDialog(
        title = stringResource(Res.string.rule_sets_add_title),
        onDismiss = onDismiss,
        buttons = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { url?.let { onAdd(it, name) } }, enabled = url != null) {
                Text(stringResource(Res.string.rule_set_download))
            }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf(
                {
                    TextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(stringResource(Res.string.rule_set_link)) },
                        placeholder = { Text(stringResource(Res.string.rule_set_link_example)) },
                        singleLine = true,
                        isError = link.isNotBlank() && url == null,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.rule_set_name_optional)) },
                        singleLine = true,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        )
        Text(
            text = stringResource(Res.string.rule_sets_formats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
    }
}

@Composable
private fun CountryDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val valid = RuleValues.Country.normalize(code)
    AppDialog(
        title = stringResource(Res.string.rule_sets_pick_country),
        onDismiss = onDismiss,
        buttons = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { valid?.let(onPick) }, enabled = valid != null) {
                Text(stringResource(Res.string.rule_set_download))
            }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf { CountryPicker(code = code, onSelect = { code = it }, modifier = Modifier.fillMaxWidth()) }
        )
    }
}

@Composable
private fun RenameDialog(current: String, placeholder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AppDialog(
        title = stringResource(Res.string.rule_set_rename),
        onDismiss = onDismiss,
        buttons = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onSave(name) }) { Text(stringResource(Res.string.save)) }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.rule_set_name)) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

private const val MINE_KEY = "mine"
private const val COUNTRIES_KEY = "countries"
