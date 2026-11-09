package io.github.yulbax.erkin.ui.screens.chains

import io.github.yulbax.erkin.ui.components.GroupListItem
import io.github.yulbax.erkin.ui.components.rememberSelection
import io.github.yulbax.erkin.ui.components.DestructiveConfirmDialog
import io.github.yulbax.erkin.ui.components.serverName
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.ChainIcon
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.components.AddFab
import io.github.yulbax.erkin.ui.components.EmptyAction
import io.github.yulbax.erkin.ui.components.EmptyState
import io.github.yulbax.erkin.ui.components.HintBanner
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.ChainsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Chains(viewModel: ChainsViewModel = koinViewModel()) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val delays by viewModel.delays.collectAsStateWithLifecycle()
    val hintSeen by viewModel.hintSeen.collectAsStateWithLifecycle()
    val chains = profiles.filter { it.kind is ServerKind.Chain }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<ProfileEntity>?>(null) }
    val selection = rememberSelection(chains.map { it.id })
    val checked = selection.checked
    val selecting = selection.active
    val toggle = { chain: ProfileEntity -> selection.toggle(chain.id) }
    val showMessage = rememberShowMessage()
    val invalid = stringResource(Res.string.chain_invalid)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
            if (!hintSeen) {
                item(key = HINT_KEY) {
                    HintBanner(
                        text = stringResource(Res.string.chains_description),
                        onDismiss = viewModel::dismissHint,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
            itemsIndexed(chains, key = { _, chain -> chain.id }) { index, chain ->
                val isChecked = chain.id in checked
                GroupListItem(index = index, count = chains.size, checked = isChecked) {
                    ChainRow(
                        chain = chain,
                        label = chainLabel(chain, profiles).orEmpty(),
                        selecting = selecting,
                        checked = isChecked,
                        onClick = { if (selecting) toggle(chain) else editing = chain },
                        onLongClick = { toggle(chain) }
                    )
                }
            }
        }
        if (chains.isEmpty()) {
            EmptyState(
                text = stringResource(Res.string.chains_empty),
                action = EmptyAction(stringResource(Res.string.add_chain)) { adding = true },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        SelectionBar(
            visible = selecting,
            count = checked.size,
            onClear = selection::clear,
            onSelectAll = selection::selectAll,
            onDelete = { deleting = chains.filter { it.id in checked } }
        )
        if (!selecting && chains.isNotEmpty()) {
            AddFab(
                onClick = { adding = true },
                contentDescription = stringResource(Res.string.add_chain),
                size = 64.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }

    if (adding || editing != null) {
        val existing = editing
        val close = {
            adding = false
            editing = null
        }
        ChainDialog(
            initial = existing,
            profiles = profiles,
            delays = delays,
            onDismiss = close,
            onSave = { name, chain ->
                viewModel.save(existing, name, chain) { saved -> if (saved) close() else showMessage(invalid) }
            },
            onDelete = existing?.let {
                {
                    viewModel.delete(listOf(it))
                    close()
                }
            }
        )
    }

    deleting?.let { targets ->
        DestructiveConfirmDialog(
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(targets)
                selection.forget(targets.map { it.id })
                deleting = null
            },
            title = { Text(stringResource(Res.string.delete_chains_title)) },
            text = { Text(stringResource(Res.string.delete_chains_message, targets.size)) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChainRow(
    chain: ProfileEntity,
    label: String,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceIcon(ChainIcon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serverName(chain.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

private const val HINT_KEY = "hint"
