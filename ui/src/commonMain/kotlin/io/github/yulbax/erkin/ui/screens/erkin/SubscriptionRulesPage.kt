package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.SkipReason
import io.github.yulbax.erkin.data.profile.SkippedRule
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.ui.components.expandedSheetState
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.screens.apps.channelColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val TARGET_ORDER = listOf(SubscriptionTarget.VPN, SubscriptionTarget.DIRECT, SubscriptionTarget.BLOCK)

internal val SubscriptionTarget.channel: ConnectionType
    get() = when (this) {
        SubscriptionTarget.VPN -> ConnectionType.VPN
        SubscriptionTarget.DIRECT -> ConnectionType.DIRECT
        SubscriptionTarget.BLOCK -> ConnectionType.BLOCK
    }

private val SkipReason.label: StringResource
    get() = when (this) {
        SkipReason.GEODATA -> Res.string.subscription_rules_skip_geodata
        SkipReason.UNSUPPORTED -> Res.string.subscription_rules_skip_unsupported
        SkipReason.NO_TARGET -> Res.string.subscription_rules_skip_no_target
        SkipReason.NO_PROVIDER -> Res.string.subscription_rules_skip_no_provider
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionRulesPage(
    subscriptionName: String,
    rules: SubscriptionRules,
    waitingLists: Set<String>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = expandedSheetState()
    val scope = rememberCoroutineScope()
    val close = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }; Unit }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.subscription_rules_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$subscriptionName · ${pluralStringResource(Res.plurals.subscription_rules_count, rules.rules.size, rules.rules.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "apply") { ApplyCard(enabled, onEnabledChange) }
            TARGET_ORDER.forEach { target ->
                val group = rules.rules.filter { it.target == target }
                if (group.isNotEmpty()) targetSection(target, group, rules, waitingLists)
            }
            if (rules.skipped.isNotEmpty()) skippedSection(rules.skipped)
        }
    }
}

@Composable
private fun ApplyCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.subscription_rules_apply), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.subscription_rules_apply_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

private fun LazyListScope.targetSection(target: SubscriptionTarget, rules: List<SubscriptionRule>, all: SubscriptionRules, waitingLists: Set<String>) {
    item(key = "target-${target.wire}") {
        val color = channelColor(target.channel)
        SectionHeader(target.channel.label(), rules.size, color)
    }
    items(rules.size, key = { "${target.wire}-$it" }) { index ->
        val (type, value) = rules[index].display
        RuleLine(type, value, all.isWaiting(rules[index], waitingLists))
    }
}

private fun LazyListScope.skippedSection(skipped: List<SkippedRule>) {
    item(key = "skipped") {
        SectionHeader(
            stringResource(Res.string.subscription_rules_skipped_title, skipped.size),
            null,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items(skipped.size, key = { "skipped-$it" }) { index ->
        SkippedLine(skipped[index])
    }
}

@Composable
private fun SectionHeader(title: String, count: Int?, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RuleLine(type: String, value: String, waiting: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TypeChip(type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (waiting) {
                Text(
                    stringResource(Res.string.subscription_rules_waiting_list),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun SkippedLine(item: SkippedRule) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(item.reason.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypeChip(type: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(type, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
