package io.github.yulbax.erkin.ui.screens.rules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.rulelist.RuleListError
import io.github.yulbax.erkin.rulelist.RuleListFailure
import io.github.yulbax.erkin.rulelist.RuleListState
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource

private val TARGET_ORDER = listOf(RuleTarget.BLOCK, RuleTarget.DIRECT, RuleTarget.BYEDPI, RuleTarget.VPN)
private val GROUP_HEIGHT = 48.dp
private val ICON_BUTTON_WIDTH = 44.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RuleTargetGroup(
    selected: RuleTarget,
    onSelect: (RuleTarget) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors()
) {
    Row(
        modifier = modifier.height(GROUP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TARGET_ORDER.forEachIndexed { index, target ->
            val isSelected = target == selected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { if (it) onSelect(target) },
                shapes = connectedShapes(index, TARGET_ORDER.size),
                colors = colors,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = if (isSelected) Modifier.weight(1f) else Modifier.width(ICON_BUTTON_WIDTH)
            ) {
                RuleTargetIcon(target, Modifier.size(16.dp))
                if (showLabel && isSelected) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = target.label(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RuleMatchGroup(
    options: List<RuleMatch>,
    selected: RuleMatch,
    onSelect: (RuleMatch) -> Unit,
    colors: ToggleButtonColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(GROUP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, match ->
            ToggleButton(
                checked = match == selected,
                onCheckedChange = { if (it) onSelect(match) },
                shapes = connectedShapes(index, options.size),
                colors = colors,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = match.shortLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
internal fun RuleDialog(
    initial: RoutingRuleEntity?,
    ruleSets: Map<String, RuleListState>,
    onDismiss: () -> Unit,
    onSave: (RuleMatch, String, RuleTarget) -> Unit,
    onDelete: (() -> Unit)?,
    onOpenRuleSets: () -> Unit,
    isNew: Boolean = initial == null
) {
    val initialMatch = initial?.let { RuleMatch.fromWire(it.type) } ?: RuleMatch.DOMAIN_SUFFIX
    var match by remember { mutableStateOf(initialMatch) }
    var text by remember { mutableStateOf(initial?.let { initialMatch.display(it.value) }.orEmpty()) }
    var target by remember { mutableStateOf(initial?.let { RuleTarget.fromWire(it.target) } ?: RuleTarget.DIRECT) }
    val normalized = match.normalize(text)
    val invalid = text.isNotBlank() && normalized == null
    val groupColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )

    AppDialog(
        title = stringResource(if (isNew) Res.string.new_rule else Res.string.edit_rule),
        onDismiss = onDismiss,
        buttons = {
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.delete)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onSave(match, text, target) }, enabled = normalized != null) {
                Text(stringResource(Res.string.save))
            }
        }
    ) {
        SectionLabel(stringResource(Res.string.rule_type))
        RuleMatchGroup(
            options = RuleMatch.entries,
            selected = match,
            onSelect = {
                if (it.ui.input != match.ui.input) text = ""
                match = it
            },
            colors = groupColors,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = match.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp)
        )
        when (match.ui.input) {
            RuleInput.RuleSet -> RuleSetChoice(
                ruleSets = ruleSets,
                selected = text,
                onSelect = { text = it },
                onOpenRuleSets = onOpenRuleSets
            )
            RuleInput.Text -> {
                GroupCard(
                    itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    items = listOf {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(Res.string.rule_value)) },
                            placeholder = { Text(match.example()) },
                            singleLine = true,
                            isError = invalid,
                            colors = transparentFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
                val hint = when {
                    invalid -> stringResource(Res.string.rule_invalid)
                    normalized != null && normalized != text.trim() -> stringResource(Res.string.rule_saved_as, normalized)
                    else -> null
                }
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp)
                    )
                }
            }
        }
        SectionLabel(stringResource(Res.string.rule_target))
        RuleTargetGroup(
            selected = target,
            onSelect = { target = it },
            showLabel = true,
            colors = groupColors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RuleSetChoice(
    ruleSets: Map<String, RuleListState>,
    selected: String,
    onSelect: (String) -> Unit,
    onOpenRuleSets: () -> Unit
) {
    if (ruleSets.isEmpty()) {
        Text(
            text = stringResource(Res.string.rule_sets_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        TextButton(onClick = onOpenRuleSets) { Text(stringResource(Res.string.rule_sets_open)) }
        return
    }
    val options = ruleSets.map { (url, state) -> Triple(url, ruleSetName(url, state), state) }.sortedBy { it.second.lowercase() }
    GroupCard(
        itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        items = options.map<Triple<String, String, RuleListState>, @Composable () -> Unit> { (url, name, state) ->
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(url) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = url == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = state.text(),
                            style = MaterialTheme.typography.bodySmall,
                            color = state.textColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(index: Int, count: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
internal fun RuleTargetIcon(target: RuleTarget, modifier: Modifier = Modifier) {
    when (val channel = target.channel) {
        null -> Icon(Icons.Default.Block, contentDescription = target.label(), modifier = modifier)
        else -> ConnectionTypeIcon(channel, modifier)
    }
}

@Composable
internal fun RuleListState.text(): String {
    val error = error
    val installed = installed
    return when {
        downloading -> stringResource(Res.string.rule_list_downloading)
        error != null && installed != null -> stringResource(Res.string.rule_list_stale, error.text(), installed.updatedAt.date())
        error != null -> error.text()
        installed != null -> stringResource(
            Res.string.rule_list_entries,
            NumberFormat.getIntegerInstance().format(entries),
            installed.updatedAt.date()
        )
        else -> stringResource(Res.string.rule_list_missing)
    }
}

@Composable
internal fun RuleListState.textColor(): Color =
    if (!downloading && error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun RuleListError.text(): String = when (failure) {
    RuleListFailure.DOWNLOAD -> stringResource(Res.string.rule_list_failed_download, detail.orEmpty())
    RuleListFailure.NOT_A_LIST -> stringResource(Res.string.rule_list_failed_not_a_list)
    RuleListFailure.EMPTY -> stringResource(Res.string.rule_list_failed_empty)
    RuleListFailure.CORRUPT -> stringResource(Res.string.rule_list_failed_corrupt)
    RuleListFailure.WRONG_KIND -> stringResource(Res.string.rule_list_failed_wrong_kind)
    RuleListFailure.TOO_LARGE -> stringResource(Res.string.rule_list_failed_too_large)
    RuleListFailure.SAVE -> stringResource(Res.string.rule_list_failed_save, detail.orEmpty())
}

private fun Long.date(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

@Composable
internal fun RuleMatch.label(): String = stringResource(ui.label)

@Composable
private fun RuleMatch.shortLabel(): String = stringResource(ui.shortLabel)

@Composable
private fun RuleMatch.example(): String = stringResource(ui.example)

@Composable
private fun RuleTarget.label(): String = channel?.label() ?: stringResource(Res.string.rule_target_block)

private val RuleTarget.channel: ConnectionType?
    get() = when (this) {
        RuleTarget.VPN -> ConnectionType.VPN
        RuleTarget.BYEDPI -> ConnectionType.BYEDPI
        RuleTarget.DIRECT -> ConnectionType.DIRECT
        RuleTarget.BLOCK -> null
    }
