package io.github.yulbax.erkin.ui.screens.rules

import io.github.yulbax.erkin.ui.components.ChoiceRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import io.github.yulbax.erkin.ui.screens.erkin.CollapsibleHeader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyListScope

@Composable
internal fun RuleAppPickerSheet(
    apps: List<RuleAppChoice>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var systemOpened by rememberSaveable { mutableStateOf(apps.any { !it.regular && it.packageName == current }) }
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
        appRows(regular, current, close, onPick)
        if (system.isNotEmpty()) {
            val systemShown = systemOpened || query.isNotBlank()
            item(key = SYSTEM_HEADER_KEY) {
                CollapsibleHeader(
                    text = "${stringResource(Res.string.rule_app_system)} · ${system.size}",
                    icon = Icons.Default.SettingsApplications,
                    expanded = systemShown
                ) { systemOpened = !systemShown }
            }
            if (systemShown) appRows(system, current, close, onPick)
        }
    }
}

private fun LazyListScope.appRows(apps: List<RuleAppChoice>, current: String?, close: (then: () -> Unit) -> Unit, onPick: (String?) -> Unit) {
    items(apps, key = { it.packageName }) { app ->
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

private const val ALL_APPS_KEY = "all"
private val APP_ROW_PADDING = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private const val SYSTEM_HEADER_KEY = "system"
