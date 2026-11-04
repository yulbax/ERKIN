package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.AppSearchBar
import io.github.yulbax.erkin.ui.platform.rememberAppIcon
import io.github.yulbax.erkin.ui.platform.platformNewAppsConnectionType
import io.github.yulbax.erkin.ui.platform.rulesCoverAllApps
import io.github.yulbax.erkin.ui.viewmodel.RuleAppChoice
import io.github.yulbax.erkin.ui.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleAppPickerSheet(
    apps: List<RuleAppChoice>,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()
    val pick: (String?) -> Unit = { app ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            onPick(app)
        }
    }
    var query by remember { mutableStateOf("") }
    val (regular, system) = apps
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        .partition { it.regular }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(Res.string.rule_app),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
        )
        AppSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.rule_app_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (query.isBlank()) {
                item(key = ALL_APPS_KEY) {
                    AppChoiceRow(
                        icon = { AllAppsIcon(40.dp) },
                        name = stringResource(Res.string.rule_app_all),
                        hint = null,
                        checked = current == null,
                        onClick = { pick(null) }
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
                        icon = { RuleAppIcon(app.packageName, app.path, app.name, 40.dp) },
                        name = app.name,
                        hint = stringResource(Res.string.rule_app_direct_hint)
                            .takeIf { !rulesCoverAllApps && (app.channel ?: platformNewAppsConnectionType) == ConnectionType.DIRECT },
                        checked = current == app.packageName,
                        onClick = { pick(app.packageName) }
                    )
                }
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
            Spacer(Modifier.width(12.dp))
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

@Composable
internal fun RuleAppIcon(packageName: String, path: String?, name: String, size: Dp) {
    val bitmap = rememberAppIcon(packageName, path)
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit)
        return
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            style = if (size < 24.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun AllAppsIcon(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val ALL_APPS_KEY = "all"
private const val SYSTEM_HEADER_KEY = "system"
