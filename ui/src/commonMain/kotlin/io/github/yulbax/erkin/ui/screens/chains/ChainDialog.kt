package io.github.yulbax.erkin.ui.screens.chains

import io.github.yulbax.erkin.ui.components.NameField
import io.github.yulbax.erkin.ui.components.EditorDialog
import io.github.yulbax.erkin.ui.components.DialogAction
import io.github.yulbax.erkin.ui.components.DestructiveButton
import io.github.yulbax.erkin.ui.components.serverNames
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import io.github.yulbax.erkin.ui.screens.apps.ServerPickerSheet
import io.github.yulbax.erkin.ui.viewmodel.CurrentServer
import io.github.yulbax.erkin.ui.viewmodel.PickerLead
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices
import androidx.compose.foundation.layout.padding
import sh.calvin.reorderable.ReorderableColumn
import io.github.yulbax.erkin.ui.components.groupItemShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.key
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChainDialog(
    initial: ProfileEntity?,
    choices: ServerChoices,
    onDismiss: () -> Unit,
    onSave: (name: String, chain: ProxyChain) -> Unit,
    onDelete: (() -> Unit)?
) {
    val links = choices.linksOnly()
    val servers = links.servers
    var picking by remember { mutableStateOf<Int?>(null) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var draft by remember { mutableStateOf(ChainDraft.of((initial?.kind as? ServerKind.Chain)?.chain, servers.firstOrNull()?.id)) }
    val byeDpiLabel = stringResource(Res.string.chain_byedpi)
    val names = serverNames()
    fun nodeLabel(node: ChainEntry): String = when (node) {
        ChainEntry.ByeDpi -> byeDpiLabel
        is ChainEntry.Server -> servers.firstOrNull { it.id == node.profileId }?.let { names.of(it.name) } ?: "—"
    }
    val chain = draft.chain
    val valid = chain != null && chain.isValid && chain.serverIds.all { id -> servers.any { it.id == id } }
    val unused = servers.firstOrNull { !draft.uses(it.id) }

    EditorDialog(
        title = stringResource(if (initial == null) Res.string.new_chain else Res.string.edit_chain),
        onDismiss = onDismiss,
        confirm = DialogAction(stringResource(Res.string.save), enabled = valid) { chain?.let { onSave(name, it) } },
        startActions = { if (onDelete != null) DestructiveButton(onDelete) }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                NameField(name, { name = it }, stringResource(Res.string.chain_name_hint))
            }
        )
        Spacer(Modifier.height(12.dp))
        ReorderableColumn(
            list = draft.nodes,
            onSettle = { from, to -> draft = draft.move(from, to) },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) { index, node, dragging ->
            key(node) {
                ReorderableItem {
                    Surface(
                        shape = groupItemShape(index, draft.nodes.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = if (dragging) 6.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!draft.isMovable(index)) {
                                Spacer(Modifier.width(HANDLE_START + HANDLE_SIZE))
                            } else {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = stringResource(Res.string.chain_move_hop),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = HANDLE_START)
                                        .draggableHandle()
                                        .padding(vertical = HANDLE_VERTICAL)
                                        .size(HANDLE_SIZE)
                                )
                            }
                            Box(modifier = Modifier.weight(1f).pullStart(FIELD_PULL)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { picking = index }
                                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            hopLabel(index, draft.lastIndex),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            nodeLabel(node),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(Res.string.apps_choose_server_cd),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (draft.isRemovable(index)) {
                                IconButton(onClick = { draft = draft.remove(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.chain_remove_hop))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (unused != null) {
            TextButton(onClick = { draft = draft.add(unused.id) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.chain_add_hop))
            }
        }
    }

    picking?.let { index ->
        draft.nodes.getOrNull(index)?.let { node ->
            ServerPickerSheet(
                subtitle = hopLabel(index, draft.lastIndex),
                choices = links,
                current = when (node) {
                    ChainEntry.ByeDpi -> CurrentServer.ByeDpi
                    is ChainEntry.Server -> CurrentServer.Bound(node.profileId)
                },
                lead = if (draft.acceptsByeDpi(index)) PickerLead.BYEDPI else PickerLead.NONE,
                onPick = { id -> draft = draft.select(index, id?.let(ChainEntry::Server) ?: ChainEntry.ByeDpi) },
                onDismiss = { picking = null }
            )
        }
    }
}

@Composable
private fun hopLabel(index: Int, lastIndex: Int): String = stringResource(
    when (index) {
        0 -> Res.string.chain_entry
        lastIndex -> Res.string.chain_exit
        else -> Res.string.chain_via
    }
)

private val HANDLE_START = 8.dp
private val HANDLE_SIZE = 24.dp
private val HANDLE_VERTICAL = 16.dp
private val FIELD_PULL = 8.dp

private fun Modifier.pullStart(pull: Dp): Modifier = layout { measurable, constraints ->
    val extra = pull.roundToPx()
    val widened = if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra) else constraints
    val placeable = measurable.measure(widened)
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
internal fun chainLabel(profile: ProfileEntity, profiles: List<ProfileEntity>): String? {
    val chain = (profile.kind as? ServerKind.Chain)?.chain ?: return null
    val names = serverNames()
    fun nameOf(id: Long) = profiles.firstOrNull { it.id == id }?.let { names.of(it.name) } ?: "—"
    val byeDpi = stringResource(Res.string.chain_byedpi)
    val hops = chain.hops.map { hop ->
        when (hop) {
            ChainEntry.ByeDpi -> byeDpi
            is ChainEntry.Server -> nameOf(hop.profileId)
        }
    }
    return (hops + nameOf(chain.exitId)).joinToString(" → ")
}
