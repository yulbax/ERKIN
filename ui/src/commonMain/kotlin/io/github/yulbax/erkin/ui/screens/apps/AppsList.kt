package io.github.yulbax.erkin.ui.screens.apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.VerticalScrollbar
import io.github.yulbax.erkin.ui.platform.HoverHint
import io.github.yulbax.erkin.ui.platform.rememberAppIcon
import io.github.yulbax.erkin.ui.platform.revealInFolder
import io.github.yulbax.erkin.ui.platform.supportsAppIcons
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.AppInfo
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppsList(
    apps: List<AppInfo>,
    vpnEnabled: Boolean,
    servers: List<ProfileEntity>,
    onConnectionTypeChange: (AppInfo, ConnectionType) -> Unit,
    onServerChange: (AppInfo, Long?) -> Unit
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(end = 8.dp),
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
                    vpnEnabled = vpnEnabled,
                    servers = servers,
                    onConnectionTypeChange = { type -> onConnectionTypeChange(app, type) },
                    onServerChange = { id -> onServerChange(app, id) }
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

@Composable
private fun AppRow(
    app: AppInfo,
    vpnEnabled: Boolean,
    servers: List<ProfileEntity>,
    onConnectionTypeChange: (ConnectionType) -> Unit,
    onServerChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val bound = servers.firstOrNull { it.id == app.serverId && !it.selected }
    val main = servers.firstOrNull { it.selected }
    var choosingServer by remember { mutableStateOf(false) }
    val canChooseServer = servers.size > 1
    val imageBitmap = rememberAppIcon(app.packageName, app.path)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (supportsAppIcons) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val location = app.path ?: app.packageName
            var truncated by remember(location) { mutableStateOf(false) }
            val reveal = revealInFolder?.takeIf { app.path != null }
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
            if (app.connectionType == ConnectionType.VPN && main != null) {
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    ServerChip(
                        label = bound?.name ?: stringResource(Res.string.app_server_main, main.name),
                        custom = bound != null,
                        onClick = if (canChooseServer) {
                            { choosingServer = true }
                        } else {
                            null
                        }
                    )
                    DropdownMenu(expanded = choosingServer, onDismissRequest = { choosingServer = false }) {
                        ServerMenuItem(
                            label = stringResource(Res.string.app_server_main, main.name),
                            checked = bound == null,
                            onClick = {
                                choosingServer = false
                                onServerChange(null)
                            }
                        )
                        servers.filterNot { it.selected }.forEach { server ->
                            ServerMenuItem(
                                label = server.name,
                                checked = server.id == bound?.id,
                                onClick = {
                                    choosingServer = false
                                    onServerChange(server.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        AppRowConnectedGroup(
            selected = app.connectionType,
            vpnEnabled = vpnEnabled,
            onSelect = onConnectionTypeChange,
            onReselect = { type -> if (type == ConnectionType.VPN && canChooseServer) choosingServer = true }
        )
    }
}

@Composable
private fun ServerChip(label: String, custom: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val content = if (custom) colors.onSecondaryContainer else colors.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (custom) colors.secondaryContainer else Color.Transparent,
        border = if (custom) null else BorderStroke(1.dp, colors.outlineVariant),
        contentColor = content
    ) {
        Row(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 6.dp, end = if (onClick != null) 2.dp else 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionTypeIcon(ConnectionType.VPN, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onClick != null) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ServerMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        trailingIcon = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else {
            null
        }
    )
}
