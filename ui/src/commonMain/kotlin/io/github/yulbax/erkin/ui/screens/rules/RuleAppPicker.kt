package io.github.yulbax.erkin.ui.screens.rules

import io.github.yulbax.erkin.ui.components.ChoiceRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.AllAppsIcon
import io.github.yulbax.erkin.ui.components.AppIcon
import io.github.yulbax.erkin.ui.components.PickerSheet
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
                ChoiceRow(
                    title = stringResource(Res.string.rule_app_all),
                    checked = current == null,
                    onClick = { close { onPick(null) } },
                    padding = APP_ROW_PADDING,
                    leading = { AllAppsIcon(40.dp, Modifier.padding(end = 12.dp)) }
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
                ChoiceRow(
                    title = app.name,
                    checked = current == app.packageName,
                    onClick = { close { onPick(app.packageName) } },
                    subtitle = stringResource(Res.string.rule_app_bypass_hint).takeIf { app.bypassed },
                    padding = APP_ROW_PADDING,
                    leading = { AppIcon(app.packageName, app.path, app.name, 40.dp, Modifier.padding(end = 12.dp)) }
                )
            }
        }
    }
}

private const val ALL_APPS_KEY = "all"
private val APP_ROW_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private const val SYSTEM_HEADER_KEY = "system"
