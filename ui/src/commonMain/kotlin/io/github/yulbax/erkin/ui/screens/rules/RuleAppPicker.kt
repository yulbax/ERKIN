package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import io.github.yulbax.erkin.ui.components.AllAppsIcon
import io.github.yulbax.erkin.ui.components.AppIcon
import io.github.yulbax.erkin.ui.components.PickerSheet
import io.github.yulbax.erkin.ui.platform.platformNewAppsConnectionType
import io.github.yulbax.erkin.ui.platform.rulesCoverAllApps
import io.github.yulbax.erkin.ui.viewmodel.RuleAppChoice
import io.github.yulbax.erkin.ui.viewmodel.matchesAppQuery
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RuleAppPickerSheet(
    apps: List<RuleAppChoice>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val (regular, system) = apps
        .filter { matchesAppQuery(it.name, it.packageName, query) }
        .partition { it.regular }

    PickerSheet(
        title = stringResource(Res.string.rule_app),
        subtitle = null,
        searchPlaceholder = stringResource(Res.string.rule_app_search),
        query = query,
        onQueryChange = { query = it },
        onDismiss = onDismiss
    ) { close ->
        if (query.isBlank()) {
            item(key = ALL_APPS_KEY) {
                AppChoiceRow(
                    icon = { AllAppsIcon(40.dp, Modifier.padding(end = 12.dp)) },
                    name = stringResource(Res.string.rule_app_all),
                    hint = null,
                    checked = current == null,
                    onClick = { close { onPick(null) } }
                )
            }
        }
        listOf(null to regular, Res.string.rule_app_system to system).forEach { (title, section) ->
            if (section.isEmpty()) return@forEach
            if (title != null) {
                item(key = SYSTEM_HEADER_KEY) {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
            }
            items(section, key = { it.packageName }) { app ->
                AppChoiceRow(
                    icon = { AppIcon(app.packageName, app.path, app.name, 40.dp, Modifier.padding(end = 12.dp)) },
                    name = app.name,
                    hint = stringResource(Res.string.rule_app_direct_hint)
                        .takeIf { !rulesCoverAllApps && (app.channel ?: platformNewAppsConnectionType) == ConnectionType.DIRECT },
                    checked = current == app.packageName,
                    onClick = { close { onPick(app.packageName) } }
                )
            }
        }
    }
}

@Composable
private fun AppChoiceRow(icon: @Composable () -> Unit, name: String, hint: String?, checked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (checked) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val ALL_APPS_KEY = "all"
private const val SYSTEM_HEADER_KEY = "system"
