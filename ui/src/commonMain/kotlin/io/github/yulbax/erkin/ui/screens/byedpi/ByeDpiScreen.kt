package io.github.yulbax.erkin.ui.screens.byedpi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.EmptyAction
import io.github.yulbax.erkin.ui.components.EmptyState
import io.github.yulbax.erkin.ui.components.RenameDialog
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.components.SwitchRow
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel.NetworkResult
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel.StrategyCard
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.ByeDpiStrategyRunner
import io.github.yulbax.erkin.vpn.StrategyRunKind
import io.github.yulbax.erkin.vpn.core.ByeDpiReachability
import io.github.yulbax.erkin.vpn.core.TestNetwork
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed interface StrategyDialog {
    data object Add : StrategyDialog
    data class Edit(val card: StrategyCard) : StrategyDialog
    data class Rename(val card: StrategyCard) : StrategyDialog
    data class Details(val id: Long) : StrategyDialog
    data object Search : StrategyDialog
}

@Composable
fun ByeDpi(viewModel: ByeDpiViewModel = koinViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<StrategyDialog?>(null) }
    var deleting by remember { mutableStateOf<List<StrategyCard>?>(null) }
    var checkedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val checked = checkedIds.filterTo(HashSet()) { id -> ui.cards.any { it.id == id } }
    val selecting = checked.isNotEmpty()
    val toggle = { card: StrategyCard ->
        checkedIds = if (card.id in checked) checked.minus(card.id).toList() else checked.plus(card.id).toList()
    }
    val run = ui.run
    val idle = run == null

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { checkedIds = emptyList() }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ui.cards.isNotEmpty()) {
                GroupCard(
                    title = stringResource(Res.string.byedpi_strategies_title),
                    items = ui.cards.map { card ->
                        {
                            StrategyRow(
                                card = card,
                                testing = run != null && card.args in run.active,
                                idle = idle,
                                selecting = selecting,
                                checked = card.id in checked,
                                onClick = { if (selecting) toggle(card) else viewModel.apply(card.args) },
                                onLongClick = { toggle(card) },
                                onDetails = { dialog = StrategyDialog.Details(card.id) },
                                onTest = { full -> viewModel.test(card.args, full) },
                                onEdit = { dialog = StrategyDialog.Edit(card) },
                                onRename = { dialog = StrategyDialog.Rename(card) },
                                onPin = { viewModel.setPinned(card.id, !card.pinned) },
                                onDelete = { viewModel.delete(card.id) }
                            )
                        }
                    }
                )
            }
        }
        if (ui.cards.isEmpty()) {
            EmptyState(
                text = stringResource(Res.string.byedpi_empty),
                action = EmptyAction(stringResource(Res.string.byedpi_search)) { dialog = StrategyDialog.Search },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        ActionMenu(
            visible = !selecting,
            running = run != null && run.kind != StrategyRunKind.Search,
            canTest = idle && ui.cards.isNotEmpty(),
            onTestAll = viewModel::testAll,
            onStop = viewModel::stop,
            onSearch = { dialog = StrategyDialog.Search },
            onAdd = { dialog = StrategyDialog.Add }
        )
        SelectionBar(
            visible = selecting,
            count = checked.size,
            onClear = { checkedIds = emptyList() },
            onSelectAll = { checkedIds = ui.cards.map { it.id } },
            onDelete = { deleting = ui.cards.filter { it.id in checked && !it.active }.ifEmpty { null } }
        )
    }

    deleting?.let { targets ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(targets)
                        checkedIds = checkedIds - targets.mapTo(HashSet()) { it.id }
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.cancel)) }
            },
            title = { Text(stringResource(Res.string.byedpi_delete_title)) },
            text = { Text(stringResource(Res.string.byedpi_delete_message, targets.size)) }
        )
    }

    when (val current = dialog) {
        StrategyDialog.Add -> StrategyEditor(
            initial = "",
            onDismiss = { dialog = null },
            onSave = { viewModel.save(it); dialog = null },
            onApply = { viewModel.apply(it); dialog = null }
        )
        is StrategyDialog.Edit -> StrategyEditor(
            initial = current.card.args,
            onDismiss = { dialog = null },
            onSave = { viewModel.edit(current.card, it); dialog = null },
            onApply = null
        )
        is StrategyDialog.Rename -> RenameDialog(
            current = current.card.name,
            placeholder = "",
            onDismiss = { dialog = null },
            onSave = { viewModel.rename(current.card.id, it); dialog = null }
        )
        is StrategyDialog.Details -> ui.cards.firstOrNull { it.id == current.id }?.let { card ->
            DetailsSheet(card, onDismiss = { dialog = null })
        }
        StrategyDialog.Search -> SearchSheet(
            ui = ui,
            onStart = viewModel::search,
            onStop = viewModel::stop,
            onApply = viewModel::apply,
            onAdd = viewModel::addFromSearch,
            onAddBest = viewModel::addBestFromSearch,
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.ActionMenu(
    visible: Boolean,
    running: Boolean,
    canTest: Boolean,
    onTestAll: () -> Unit,
    onStop: () -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded && visible && !running
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = open,
        onBackCompleted = { expanded = false }
    )
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd)
    ) {
        FloatingActionButtonMenu(
            expanded = open,
            button = {
                ToggleFloatingActionButton(
                    checked = open,
                    onCheckedChange = { if (running) onStop() else expanded = !expanded }
                ) {
                    val icon = when {
                        running -> Icons.Filled.Stop
                        checkedProgress > 0.5f -> Icons.Filled.Close
                        else -> Icons.Filled.Add
                    }
                    key(icon) {
                        Icon(
                            painter = rememberVectorPainter(icon),
                            contentDescription = stringResource(if (running) Res.string.byedpi_stop else Res.string.byedpi_add_strategy),
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                }
            }
        ) {
            val pick = { action: () -> Unit ->
                expanded = false
                action()
            }
            if (canTest) {
                FloatingActionButtonMenuItem(
                    onClick = { pick(onTestAll) },
                    text = { Text(stringResource(Res.string.byedpi_test_all)) },
                    icon = { Icon(Icons.Filled.DoneAll, contentDescription = null) }
                )
            }
            FloatingActionButtonMenuItem(
                onClick = { pick(onSearch) },
                text = { Text(stringResource(Res.string.byedpi_search)) },
                icon = { Icon(Icons.Filled.TravelExplore, contentDescription = null) }
            )
            FloatingActionButtonMenuItem(
                onClick = { pick(onAdd) },
                text = { Text(stringResource(Res.string.byedpi_add_strategy)) },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RunProgress(run: ByeDpiStrategyRunner.Run, onStop: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.byedpi_testing, run.done, run.total),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearWavyProgressIndicator(
                    progress = { if (run.total > 0) run.done.toFloat() / run.total else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            TextButton(
                onClick = onStop,
                contentPadding = ButtonDefaults.ExtraSmallContentPadding,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp).heightIn(min = ButtonDefaults.ExtraSmallContainerHeight)
            ) { Text(stringResource(Res.string.byedpi_stop)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StrategyRow(
    card: StrategyCard,
    testing: Boolean,
    idle: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetails: () -> Unit,
    onTest: (full: Boolean) -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = card.active, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            if (card.name.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.pinned) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.pinned && card.name.isBlank()) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = card.args,
                    style = if (card.name.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (card.name.isBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (testing) {
            CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            card.current?.let { ScoreBadge(it.score) }
        }
        if (selecting) {
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
        } else {
            StrategyMenu(card, idle, onDetails, onTest, onEdit, onRename, onPin, onDelete)
        }
    }
}

@Composable
private fun ScoreBadge(score: ByeDpiReachability) {
    if (score.total == 0) return
    val color = scoreColor(score)
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text = "${score.percent}%",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StrategyMenu(
    card: StrategyCard,
    idle: Boolean,
    onDetails: () -> Unit,
    onTest: (full: Boolean) -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.more_options_cd))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.byedpi_details)) }, onClick = { open = false; onDetails() })
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.byedpi_test)) },
                enabled = idle,
                onClick = { open = false; onTest(false) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.byedpi_test_full, ByeDpiSites.fullTotal)) },
                enabled = idle,
                onClick = { open = false; onTest(true) }
            )
            DropdownMenuItem(text = { Text(stringResource(Res.string.byedpi_edit)) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.rename)) }, onClick = { open = false; onRename() })
            DropdownMenuItem(
                text = { Text(stringResource(if (card.pinned) Res.string.byedpi_unpin else Res.string.byedpi_pin)) },
                onClick = { open = false; onPin() }
            )
            if (!card.active) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.delete)) }, onClick = { open = false; onDelete() })
            }
        }
    }
}

@Composable
private fun StrategyEditor(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onApply: ((String) -> Unit)?) {
    var text by remember { mutableStateOf(initial) }
    AppDialog(
        title = stringResource(if (initial.isEmpty()) Res.string.byedpi_add_strategy else Res.string.byedpi_edit),
        onDismiss = onDismiss,
        buttons = {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.save)) }
            if (onApply != null) {
                TextButton(onClick = { onApply(text) }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.byedpi_apply)) }
            }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.byedpi_args_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(card: StrategyCard, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (card.name.isNotBlank()) Text(card.name, style = MaterialTheme.typography.titleMedium)
            Text(card.args, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            if (card.results.isEmpty()) {
                Text(
                    stringResource(Res.string.byedpi_not_tested),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            card.results.forEach { NetworkSummary(it) }
        }
    }
}

@Composable
private fun NetworkSummary(result: NetworkResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = result.network.label(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ScoreBadge(result.score)
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.testedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (result.groups.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                result.groups.forEach { score ->
                    Text(
                        text = "● ${score.group} ${score.reachability.reachable}/${score.reachability.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor(score.reachability)
                    )
                }
            }
        }
        result.sites.sortedBy { it.host }.forEach { site ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (site.reachable) "✓" else "✗",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (site.reachable) StatusGreen else MaterialTheme.colorScheme.error
                )
                Text(site.host, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    ui: ByeDpiViewModel.UiState,
    onStart: (sni: String, custom: String?) -> Unit,
    onStop: () -> Unit,
    onApply: (String) -> Unit,
    onAdd: (String) -> Unit,
    onAddBest: () -> Unit,
    onDismiss: () -> Unit
) {
    var sni by rememberSaveable { mutableStateOf("") }
    var useCustom by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    val run = ui.run
    val searching = run?.kind == StrategyRunKind.Search
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = expandedSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(Res.string.byedpi_search), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            GroupCard(
                items = listOfNotNull(
                    {
                        TextField(
                            value = sni,
                            onValueChange = { sni = it },
                            singleLine = true,
                            label = { Text(stringResource(Res.string.byedpi_search_sni)) },
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    { SwitchRow(stringResource(Res.string.byedpi_search_custom), useCustom) { useCustom = it } },
                    if (useCustom) {
                        {
                            TextField(
                                value = custom,
                                onValueChange = { custom = it },
                                placeholder = { Text(stringResource(Res.string.byedpi_search_custom_hint)) },
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                colors = transparentFieldColors(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            )
            if (searching) {
                RunProgress(run, onStop)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onStart(sni, custom.takeIf { useCustom }) },
                        enabled = run == null && (!useCustom || custom.isNotBlank())
                    ) { Text(stringResource(Res.string.byedpi_search_start)) }
                    if (ui.search.any { !it.saved && it.score.reachable > 0 }) {
                        TextButton(onClick = onAddBest) { Text(stringResource(Res.string.byedpi_add_best)) }
                    }
                }
            }
            if (ui.search.isNotEmpty()) {
                GroupCard(items = ui.search.map { row ->
                    {
                        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = row.args,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                ScoreBadge(row.score)
                            }
                            Row {
                                TextButton(onClick = { onApply(row.args) }) { Text(stringResource(Res.string.byedpi_apply)) }
                                TextButton(onClick = { onAdd(row.args) }, enabled = !row.saved) {
                                    Text(stringResource(if (row.saved) Res.string.byedpi_saved else Res.string.byedpi_add))
                                }
                            }
                        }
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun expandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
)

@Composable
private fun TestNetwork.label(): String = when (this) {
    TestNetwork.Wifi -> stringResource(Res.string.network_wifi)
    TestNetwork.Ethernet -> stringResource(Res.string.network_ethernet)
    is TestNetwork.Cellular -> listOf(stringResource(Res.string.network_cellular), operator)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    TestNetwork.Unknown -> stringResource(Res.string.network_unknown)
}

@Composable
private fun scoreColor(score: ByeDpiReachability): Color = when {
    score.total > 0 && score.reachable == score.total -> StatusGreen
    score.reachable == 0 -> MaterialTheme.colorScheme.error
    else -> StatusAmber
}
