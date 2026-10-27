package io.github.yulbax.erkin.ui.screens.byedpi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.RenameDialog
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel.ArgsCard
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel.NetworkResult
import io.github.yulbax.erkin.ui.viewmodel.ByeDpiViewModel.TestProgress
import io.github.yulbax.erkin.vpn.ByeDpiSites
import io.github.yulbax.erkin.vpn.core.TestNetwork
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ByeDpi(viewModel: ByeDpiViewModel = koinViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var renaming by remember { mutableStateOf<ArgsCard?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ArgsEditor(ui.args, viewModel.argsDefault, onApply = viewModel::apply)
        GroupCard(
            title = stringResource(Res.string.byedpi_history_title),
            description = if (ui.running) null else stringResource(Res.string.byedpi_not_running),
            items = ui.cards.map { card ->
                {
                    ArgsCardItem(
                        card = card,
                        expanded = card.id in expanded,
                        running = ui.running,
                        testing = ui.testing,
                        onToggle = { expanded = if (card.id in expanded) expanded - card.id else expanded + card.id },
                        onApply = { viewModel.apply(card.args) },
                        onTest = { full -> viewModel.test(card.args, full) },
                        onStop = viewModel::stopTest,
                        onRename = { renaming = card },
                        onPin = { viewModel.setPinned(card.id, !card.pinned) },
                        onDelete = { viewModel.delete(card.id) }
                    )
                }
            }
        )
    }

    renaming?.let { card ->
        RenameDialog(
            current = card.name,
            placeholder = "",
            onDismiss = { renaming = null },
            onSave = { name ->
                viewModel.rename(card.id, name)
                renaming = null
            }
        )
    }
}

@Composable
private fun ArgsEditor(saved: String, default: String, onApply: (String) -> Unit) {
    var text by remember { mutableStateOf(saved) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (!focused) text = saved }

    GroupCard(
        title = stringResource(Res.string.byedpi_cli_args),
        action = {
            Text(
                text = stringResource(Res.string.reset_to_default),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    text = ""
                    onApply("")
                }
            )
        },
        items = listOf {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(default, fontFamily = FontFamily.Monospace) },
                label = { Text(stringResource(Res.string.byedpi_args_label)) },
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { st ->
                        if (focused && !st.isFocused && text.trim() != saved.trim()) onApply(text)
                        focused = st.isFocused
                    }
            )
        }
    )
}

@Composable
private fun ArgsCardItem(
    card: ArgsCard,
    expanded: Boolean,
    running: Boolean,
    testing: TestProgress?,
    onToggle: () -> Unit,
    onApply: () -> Unit,
    onTest: (full: Boolean) -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val canTest = running && testing == null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card.pinned) {
                Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (card.active) {
                Text(
                    text = stringResource(Res.string.byedpi_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            CardMenu(card, canTest, onTest, onRename, onPin, onDelete)
        }
        Text(
            text = card.args,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp)
        )
        if (card.results.isEmpty()) {
            Text(
                text = stringResource(Res.string.byedpi_not_tested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        card.results.forEach { result -> NetworkSummary(result, expanded) }
        if (testing != null && testing.args == card.args) {
            Text(
                text = stringResource(Res.string.byedpi_testing, testing.done, testing.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { if (testing.total > 0) testing.done.toFloat() / testing.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            )
            TextButton(onClick = onStop) { Text(stringResource(Res.string.cancel)) }
        } else if (card.active) {
            TextButton(onClick = { onTest(false) }, enabled = canTest) { Text(stringResource(Res.string.byedpi_test)) }
        } else {
            Row {
                TextButton(onClick = onApply, enabled = testing == null) { Text(stringResource(Res.string.byedpi_apply)) }
                TextButton(onClick = { onTest(false) }, enabled = canTest) {
                    Text(stringResource(Res.string.byedpi_apply_and_test))
                }
            }
        }
    }
}

@Composable
private fun CardMenu(
    card: ArgsCard,
    canTest: Boolean,
    onTest: (full: Boolean) -> Unit,
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
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.byedpi_test_full, ByeDpiSites.fullTotal)) },
                enabled = canTest,
                onClick = { open = false; onTest(true) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.rename)) },
                onClick = { open = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (card.pinned) Res.string.byedpi_unpin else Res.string.byedpi_pin)) },
                onClick = { open = false; onPin() }
            )
            if (!card.active) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.delete)) },
                    onClick = { open = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun NetworkSummary(result: NetworkResult, expanded: Boolean) {
    Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = result.network.label(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            LinearProgressIndicator(
                progress = { if (result.total > 0) result.reachable.toFloat() / result.total else 0f },
                color = scoreColor(result.reachable, result.total),
                modifier = Modifier.width(56.dp)
            )
            Text(
                text = "${percent(result.reachable, result.total)}%",
                style = MaterialTheme.typography.labelMedium,
                color = scoreColor(result.reachable, result.total)
            )
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
                        text = "● ${score.group}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor(score.reachable, score.total)
                    )
                }
            }
        }
        if (expanded) {
            result.sites.sortedBy { it.host }.forEach { site ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (site.reachable) "✓" else "✗",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (site.reachable) StatusGreen else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = site.host,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

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
private fun scoreColor(reachable: Int, total: Int): Color = when {
    total > 0 && reachable == total -> StatusGreen
    reachable == 0 -> MaterialTheme.colorScheme.error
    else -> StatusAmber
}

private fun percent(reachable: Int, total: Int): Int = if (total > 0) reachable * 100 / total else 0
