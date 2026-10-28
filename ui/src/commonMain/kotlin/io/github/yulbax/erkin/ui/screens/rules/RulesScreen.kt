package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.ui.components.AddFab
import io.github.yulbax.erkin.ui.components.EmptyAction
import io.github.yulbax.erkin.ui.components.EmptyState
import io.github.yulbax.erkin.ui.components.groupItemShape
import io.github.yulbax.erkin.ui.components.HintBanner
import io.github.yulbax.erkin.ui.components.SelectionBar
import io.github.yulbax.erkin.ui.platform.rulesCoverAllApps
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.RulesViewModel
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun Rules(onOpenRuleSets: () -> Unit, viewModel: RulesViewModel = koinViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val hintSeen by viewModel.hintSeen.collectAsStateWithLifecycle()
    val listStates by viewModel.listStates.collectAsStateWithLifecycle()
    val stateOf = { rule: RoutingRuleEntity ->
        rule.toRule()?.let(RuleLists::url)?.let { listStates[it] ?: RuleListState() }
    }
    var editing by remember { mutableStateOf<RoutingRuleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<List<RoutingRuleEntity>?>(null) }
    var checkedIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val checked = checkedIds.filterTo(HashSet()) { id -> rules.any { it.id == id } }
    val selecting = checked.isNotEmpty()
    val toggle = { rule: RoutingRuleEntity ->
        checkedIds = if (rule.id in checked) checked.minus(rule.id).toList() else checked.plus(rule.id).toList()
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { checkedIds = emptyList() }
    )

    var order by remember { mutableStateOf(rules) }
    LaunchedEffect(rules) { order = rules }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOfFirst { it.id == from.key }
        val toIndex = order.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val hint = stringResource(Res.string.rules_description) + "\n\n" +
        stringResource(if (rulesCoverAllApps) Res.string.rules_scope_all else Res.string.rules_scope_tunneled)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!hintSeen) {
                item(key = HINT_KEY) {
                    HintBanner(
                        text = hint,
                        onDismiss = viewModel::dismissHint,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
            itemsIndexed(order, key = { _, rule -> rule.id }) { index, rule ->
                ReorderableItem(reorderState, key = rule.id) { dragging ->
                    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp)
                    val isChecked = rule.id in checked
                    Surface(
                        shape = if (dragging) groupItemShape(0, 1) else groupItemShape(index, order.size),
                        shadowElevation = elevation,
                        color = if (isChecked) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp)
                    ) {
                        RuleRow(
                            rule = rule,
                            listState = stateOf(rule),
                            selecting = selecting,
                            checked = isChecked,
                            onClick = { if (selecting) toggle(rule) else editing = rule },
                            onLongClick = { toggle(rule) },
                            onTargetChange = { viewModel.setTarget(rule, it) },
                            handle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStopped = { viewModel.reorder(order.map { it.id }) }
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        if (order.isEmpty()) {
            EmptyState(
                text = stringResource(Res.string.rules_empty),
                action = EmptyAction(stringResource(Res.string.add_rule)) { adding = true },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        SelectionBar(
            visible = selecting,
            count = checked.size,
            onClear = { checkedIds = emptyList() },
            onSelectAll = { checkedIds = rules.map { it.id } },
            onDelete = { deleting = rules.filter { it.id in checked } }
        )
        if (!selecting && order.isNotEmpty()) {
            AddFab(
                onClick = { adding = true },
                contentDescription = stringResource(Res.string.add_rule),
                size = 64.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    if (adding || editing != null) {
        val existing = editing
        RuleDialog(
            initial = existing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { match, raw, target ->
                viewModel.save(existing, match, raw, target)
                adding = false
                editing = null
            },
            onDelete = existing?.let {
                {
                    editing = null
                    deleting = listOf(it)
                }
            },
            ruleSets = listStates,
            onOpenRuleSets = {
                adding = false
                editing = null
                onOpenRuleSets()
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
            title = { Text(stringResource(Res.string.delete_rules_title)) },
            text = {
                val single = targets.singleOrNull()
                if (single != null) {
                    Text(stringResource(Res.string.delete_rule_message, single.displayValue(stateOf(single))))
                } else {
                    Text(stringResource(Res.string.delete_rules_message, targets.size))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleRow(
    rule: RoutingRuleEntity,
    listState: RuleListState?,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTargetChange: (RuleTarget) -> Unit,
    handle: @Composable () -> Unit
) {
    val match = RuleMatch.fromWire(rule.type)
    val target = RuleTarget.fromWire(rule.target)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        handle()
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.displayValue(listState),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (match != null) {
                Text(
                    text = listState?.text() ?: match.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = listState?.textColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (selecting) {
            Checkbox(checked = checked, onCheckedChange = null)
        } else if (target != null) {
            RuleTargetGroup(
                selected = target,
                onSelect = onTargetChange,
                modifier = Modifier.width(ROW_TARGET_WIDTH)
            )
        }
    }
}

@Composable
private fun RoutingRuleEntity.displayValue(listState: RuleListState?): String {
    val match = RuleMatch.fromWire(type) ?: return value
    return when (match.ui.input) {
        RuleInput.Text -> match.display(value)
        RuleInput.RuleSet -> ruleSetName(value, listState)
    }
}

private const val HINT_KEY = "hint"
private val ROW_TARGET_WIDTH = 190.dp
