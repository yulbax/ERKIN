package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.RuleListStatus
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
    onDismiss: () -> Unit,
    onSave: (RuleMatch, String, RuleTarget) -> Unit,
    onDelete: (() -> Unit)?,
    countryAvailable: Boolean,
    listStatus: (String) -> RuleListStatus?,
    onUpdateList: (String) -> Unit
) {
    val initialMatch = initial?.let { RuleMatch.fromWire(it.type) } ?: RuleMatch.DOMAIN_SUFFIX
    val options = RuleMatch.entries.filter { !it.requiresGeoIp || countryAvailable || it == initialMatch }
    var match by remember { mutableStateOf(initialMatch) }
    var text by remember { mutableStateOf(initial?.let { initialMatch.display(it.value) }.orEmpty()) }
    var target by remember { mutableStateOf(initial?.let { RuleTarget.fromWire(it.target) } ?: RuleTarget.DIRECT) }
    val normalized = match.normalize(text)
    val invalid = text.isNotBlank() && normalized == null
    val status = normalized?.takeIf { match.ui.input == RuleInput.Link }?.let(listStatus)
    val groupColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )

    AppDialog(
        title = stringResource(if (initial == null) Res.string.new_rule else Res.string.edit_rule),
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
            options = options,
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
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                if (match.ui.input == RuleInput.Country) {
                    CountryPicker(code = text, onSelect = { text = it }, modifier = Modifier.fillMaxWidth())
                } else {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(Res.string.rule_value)) },
                        placeholder = { Text(match.example()) },
                        trailingIcon = status?.let {
                            {
                                if (it == RuleListStatus.Downloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { onUpdateList(normalized) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.rule_list_update))
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        isError = invalid,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
        val hint = when {
            match.ui.input == RuleInput.Country -> null
            invalid -> stringResource(Res.string.rule_invalid)
            status != null -> status.text()
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
        if (match.ui.input == RuleInput.Link && text.isBlank()) {
            SectionLabel(stringResource(Res.string.rule_list_presets))
            RuleListPreset.entries.forEach { preset ->
                SuggestionChip(onClick = { text = preset.url }, label = { Text(stringResource(preset.label)) })
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
internal fun RuleListStatus.text(): String = when (this) {
    RuleListStatus.Missing -> stringResource(Res.string.rule_list_missing)
    RuleListStatus.Downloading -> stringResource(Res.string.rule_list_downloading)
    is RuleListStatus.Ready -> stringResource(
        Res.string.rule_list_entries,
        NumberFormat.getIntegerInstance().format(list.entries),
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(list.updatedAt))
    )
}

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
