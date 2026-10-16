package io.github.yulbax.erkin.ui.screens.apps

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.VerticalScrollbar
import io.github.yulbax.erkin.ui.platform.HoverHint
import io.github.yulbax.erkin.ui.platform.rememberAppIcon
import io.github.yulbax.erkin.ui.platform.revealInFolder
import io.github.yulbax.erkin.ui.platform.supportsAppIcons
import io.github.yulbax.erkin.ui.viewmodel.AppInfo

@Composable
internal fun AppsList(
    apps: List<AppInfo>,
    vpnEnabled: Boolean,
    onConnectionTypeChange: (String, String, Boolean, ConnectionType) -> Unit
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
                    onConnectionTypeChange = { type ->
                        onConnectionTypeChange(app.packageName, app.name, app.isSystemApp, type)
                    }
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
    onConnectionTypeChange: (ConnectionType) -> Unit,
    modifier: Modifier = Modifier
) {
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
        }

        Spacer(modifier = Modifier.width(6.dp))

        AppRowConnectedGroup(
            selected = app.connectionType,
            vpnEnabled = vpnEnabled,
            onSelect = onConnectionTypeChange
        )
    }
}
