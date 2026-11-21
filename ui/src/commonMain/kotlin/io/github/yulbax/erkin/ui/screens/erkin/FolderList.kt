package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerFolder
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.data.profile.ServerTree
import io.github.yulbax.erkin.ui.components.Selection
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.filled.Delete
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class FolderKind {
    ROOT,
    LOCAL,
    SUBSCRIPTION;

    fun actionsFor(checked: Collection<Long>, hidden: Set<Long>): List<BulkAction> = when (this) {
        ROOT -> listOf(BulkAction.MOVE, BulkAction.DELETE)
        LOCAL -> listOf(BulkAction.MOVE, BulkAction.REMOVE_FROM_GROUP, BulkAction.DELETE)
        SUBSCRIPTION -> listOfNotNull(
            BulkAction.HIDE.takeIf { checked.any { it !in hidden } },
            BulkAction.SHOW.takeIf { checked.any { it in hidden } }
        )
    }

    companion object {
        fun of(folder: ServerFolder, tree: ServerTree): FolderKind = when (folder) {
            ServerFolder.Root -> ROOT
            is ServerFolder.Group -> if (tree.group(folder.id)?.kind is GroupKind.Subscription) SUBSCRIPTION else LOCAL
        }
    }
}

internal enum class BulkAction(val icon: ImageVector, val label: StringResource) {
    MOVE(Icons.AutoMirrored.Filled.DriveFileMove, Res.string.move_to_group),
    REMOVE_FROM_GROUP(Icons.Default.FolderOff, Res.string.remove_from_group),
    HIDE(Icons.Default.VisibilityOff, Res.string.hide_server),
    SHOW(Icons.Default.Visibility, Res.string.show_server),
    DELETE(Icons.Default.Delete, Res.string.delete);

    val clearsAtOnce: Boolean get() = this == REMOVE_FROM_GROUP || this == HIDE || this == SHOW

    fun keepsAServerVisible(checked: List<Long>, visible: Set<Long>): Boolean =
        this != HIDE || visible.any { it !in checked }

    fun targets(checked: List<Long>, hidden: Set<Long>): List<Long> = when (this) {
        HIDE -> checked.filterNot { it in hidden }
        SHOW -> checked.filter { it in hidden }
        MOVE, REMOVE_FROM_GROUP, DELETE -> checked
    }
}

internal class ServerBulkActions(
    val onSetHidden: (ids: List<Long>, hidden: Boolean) -> Unit,
    val onDelete: (ids: List<Long>) -> Unit,
    val onRemoveFromGroup: (ids: List<Long>) -> Unit,
    val onMove: (ids: List<Long>, done: () -> Unit) -> Unit,
    val canMove: (ids: List<Long>) -> Boolean
) {
    fun run(action: BulkAction, ids: List<Long>, done: () -> Unit) {
        when (action) {
            BulkAction.MOVE -> onMove(ids, done)
            BulkAction.DELETE -> onDelete(ids)
            BulkAction.REMOVE_FROM_GROUP -> onRemoveFromGroup(ids)
            BulkAction.HIDE -> onSetHidden(ids, true)
            BulkAction.SHOW -> onSetHidden(ids, false)
        }
        if (action.clearsAtOnce) done()
    }
}

internal fun selectableIds(nodes: List<ServerNode>, hidden: List<ProfileEntity>, hiddenShown: Boolean): List<Long> =
    nodes.flatMap { it.profileIds } + if (hiddenShown) hidden.map { it.id } else emptyList()

internal class RowCheck(val selecting: Boolean, val state: ToggleableState, val onToggle: () -> Unit) {
    fun modeWith(handle: @Composable () -> Unit): RowMode = if (selecting) RowMode.Selecting(state, handle) else RowMode.Normal
}

@Composable
internal fun FolderList(
    nodes: List<ServerNode>,
    kind: FolderKind,
    selection: Selection,
    bulk: ServerBulkActions,
    row: @Composable (node: ServerNode, check: RowCheck, handle: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    hidden: List<ProfileEntity> = emptyList(),
    visibleInFolder: Set<Long> = emptySet(),
    listState: LazyListState = rememberLazyListState(),
    onReorder: ((List<Long>) -> Unit)? = null,
    emptyText: String? = null,
    sidePadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
    keepBarRoom: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    var hiddenShown by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf(nodes) }
    LaunchedEffect(nodes) { order = nodes }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.key == from.key }
        val toIndex = order.indexOfFirst { it.key == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
    fun checkOf(ids: List<Long>) = RowCheck(selection.active, selection.stateOf(ids)) { selection.toggleAll(ids) }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = sidePadding,
                end = sidePadding,
                top = topPadding,
                bottom = if (keepBarRoom || selection.active) BAR_ROOM else LIST_END
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(order, key = { it.key }) { node ->
                ReorderableItem(reorderState, key = node.key) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp)
                    val handle: @Composable () -> Unit = when (onReorder) {
                        null -> ({})
                        else -> ({
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(onDragStopped = { onReorder(order.flatMap { it.storedIds }) })
                            ) {
                                Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        })
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = elevation,
                        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
                    ) {
                        row(node, checkOf(node.profileIds), handle)
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item(key = HIDDEN_HEADER_KEY) {
                    HiddenHeader(count = hidden.size, expanded = hiddenShown) { hiddenShown = !hiddenShown }
                }
                if (hiddenShown) {
                    items(hidden, key = { it.id }) { profile -> row(ServerNode.Server(profile), checkOf(listOf(profile.id))) {} }
                }
            }
        }
        if (emptyText != null && order.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        }
        overlay()
        FolderSelectionBar(kind, selection, bulk, selectableIds(order, hidden, hiddenShown), hidden.mapTo(HashSet()) { it.id }, visibleInFolder)
    }
}

@Composable
private fun BoxScope.FolderSelectionBar(
    kind: FolderKind,
    selection: Selection,
    bulk: ServerBulkActions,
    shownIds: List<Long>,
    hiddenIds: Set<Long>,
    visibleInFolder: Set<Long>
) {
    val checked = selection.checked.toList()
    val actions = kind.actionsFor(checked, hiddenIds)
    fun run(action: BulkAction) = bulk.run(action, action.targets(checked, hiddenIds), selection::clear)
    SelectionBar(
        visible = selection.active,
        count = checked.size,
        onClear = selection::clear,
        onSelectAll = { selection.select(shownIds) },
        onDelete = if (BulkAction.DELETE in actions) ({ run(BulkAction.DELETE) }) else null
    ) {
        actions.filter { it != BulkAction.DELETE }.forEach { action ->
            val enabled = action.keepsAServerVisible(checked, visibleInFolder) && (action != BulkAction.MOVE || bulk.canMove(checked))
            IconButton(onClick = { run(action) }, enabled = enabled) {
                Icon(action.icon, contentDescription = stringResource(action.label))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HiddenHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(Res.string.hidden_servers, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            val turn by animateFloatAsState(if (expanded) 180f else 0f)
            FilledRowToggle(checked = expanded, onCheckedChange = { onToggle() }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(turn))
            }
        }
    }
}

private const val HIDDEN_HEADER_KEY = "hidden"
private val BAR_ROOM = 72.dp
private val LIST_END = 24.dp
