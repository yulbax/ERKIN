package io.github.yulbax.erkin.ui.screens.chains

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.AddFab
import io.github.yulbax.erkin.ui.components.GroupPlaceholder
import io.github.yulbax.erkin.ui.components.HintBanner
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.components.groupItemShape
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.ChainsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Chains(viewModel: ChainsViewModel = koinViewModel()) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val hintSeen by viewModel.hintSeen.collectAsStateWithLifecycle()
    val chains = profiles.filter { it.kind is ServerKind.Chain }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<ProfileEntity>?>(null) }
    var checkedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val checked = checkedIds.filterTo(HashSet()) { id -> chains.any { it.id == id } }
    val selecting = checked.isNotEmpty()
    val toggle = { chain: ProfileEntity ->
        checkedIds = if (chain.id in checked) checked.minus(chain.id).toList() else checked.plus(chain.id).toList()
    }
    val showMessage = rememberShowMessage()
    val invalid = stringResource(Res.string.chain_invalid)

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { checkedIds = emptyList() }
    )

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
            if (chains.isEmpty()) {
                item(key = EMPTY_KEY) {
                    GroupPlaceholder(
                        text = stringResource(Res.string.chains_empty),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            itemsIndexed(chains, key = { _, chain -> chain.id }) { index, chain ->
                val isChecked = chain.id in checked
                Surface(
                    shape = groupItemShape(index, chains.size),
                    color = if (isChecked) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
                ) {
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
        SelectionBar(
            visible = selecting,
            count = checked.size,
            onClear = { checkedIds = emptyList() },
            onSelectAll = { checkedIds = chains.map { it.id } },
            onDelete = { deleting = chains.filter { it.id in checked } }
        )
        if (!selecting) {
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
        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chain.name.ifEmpty { stringResource(Res.string.unnamed_profile) },
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
private const val EMPTY_KEY = "empty"
