package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.AppIcon
import io.github.yulbax.erkin.ui.components.VerticalScrollbar
import io.github.yulbax.erkin.ui.platform.HoverHint
import io.github.yulbax.erkin.ui.platform.revealInFolder
import io.github.yulbax.erkin.ui.platform.scrollbarAlwaysVisible
import io.github.yulbax.erkin.ui.viewmodel.AppInfo
import io.github.yulbax.erkin.ui.viewmodel.AppRowMode
import io.github.yulbax.erkin.ui.viewmodel.AppsMode
import io.github.yulbax.erkin.ui.viewmodel.ServerChoices

@Composable
internal fun AppsList(
    apps: List<AppInfo>,
    mode: AppsMode,
    choices: ServerChoices,
    onConnectionTypeChange: (AppInfo, ConnectionType) -> Unit,
    onChooseServer: (AppInfo) -> Unit,
    onToggle: (String) -> Unit,
    onStartSelecting: (String) -> Unit
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(end = if (scrollbarAlwaysVisible) 8.dp else 0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = apps.size,
                key = { apps[it].packageName },
                contentType = { "app_row" }
            ) { index ->
                val app = apps[index]
                AppRow(
                    app = app,
                    mode = mode.rowMode(app.packageName),
                    choices = choices,
                    onConnectionTypeChange = { type -> onConnectionTypeChange(app, type) },
                    onChooseServer = { onChooseServer(app) },
                    onToggle = { onToggle(app.packageName) },
                    onStartSelecting = { onStartSelecting(app.packageName) }
                )
                if (index < apps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = 8.dp),
            sections = remember(apps) { appSections(apps) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    mode: AppRowMode,
    choices: ServerChoices,
    onConnectionTypeChange: (ConnectionType) -> Unit,
    onChooseServer: () -> Unit,
    onToggle: () -> Unit,
    onStartSelecting: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val browsing = mode == AppRowMode.Browsing
    val checked = mode == AppRowMode.Selecting(checked = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = if (browsing) ({ menuOpen = true }) else onToggle,
                onLongClick = if (browsing) onStartSelecting else onToggle
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLeadingIcon(app, checked)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AppLocation(app, revealable = browsing)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            ChannelButton(
                type = app.connectionType,
                server = choices.labelFor(app),
                enabled = browsing,
                onClick = { menuOpen = true }
            )
            ChannelMenu(
                expanded = menuOpen && browsing,
                current = app.connectionType,
                vpnEnabled = choices.vpnEnabled,
                server = choices.menuServer(app.connectionType, app.serverId),
                onDismiss = { menuOpen = false },
                onSelect = { type ->
                    menuOpen = false
                    if (type != app.connectionType) onConnectionTypeChange(type)
                },
                onChooseServer = {
                    menuOpen = false
                    onChooseServer()
                }
            )
        }
    }
}

@Composable
private fun AppLeadingIcon(app: AppInfo, checked: Boolean) {
    if (!checked) {
        AppIcon(app.packageName, app.path, app.name, 40.dp, Modifier.padding(end = 12.dp))
        return
    }
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun AppLocation(app: AppInfo, revealable: Boolean) {
    val location = app.path ?: app.packageName
    var truncated by remember(location) { mutableStateOf(false) }
    val reveal = revealInFolder?.takeIf { app.path != null && revealable }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    HoverHint(text = location, enabled = truncated) {
        Text(
            text = location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (reveal != null && hovered) TextDecoration.Underline else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { truncated = it.hasVisualOverflow },
            modifier = if (reveal != null) {
                Modifier.clickable(interactionSource = interaction, indication = null) { reveal(location) }
            } else {
                Modifier
            }
        )
    }
}
