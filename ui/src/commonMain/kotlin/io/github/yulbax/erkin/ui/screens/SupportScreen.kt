package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.IconSource
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.platform.rememberCopyText
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SupportAction(val icon: ImageVector) {
    OPEN(Icons.AutoMirrored.Filled.OpenInNew),
    COPY(Icons.Filled.ContentCopy)
}

enum class SupportMethod(
    val label: StringResource,
    val icon: IconSource,
    val value: String,
    val action: SupportAction
) {
    BOOSTY(Res.string.support_boosty, IconSource.Drawable(Res.drawable.ic_boosty), "https://boosty.to/yulbax/donate", SupportAction.OPEN),
    TON(Res.string.support_ton, IconSource.Drawable(Res.drawable.ic_ton), "UQBY7tgHXXhPjeco5WmZ8XKFOBl_9Iy2faAqMvQcniq-Lr06", SupportAction.COPY),
    TRON(Res.string.support_tron, IconSource.Drawable(Res.drawable.ic_tether), "TF4EfD66Dr2414RRcU7nmQEMo87TxtVCMN", SupportAction.COPY)
}

@Composable
fun Support() {
    val uriHandler = LocalUriHandler.current
    val copy = rememberCopyText(stringResource(Res.string.support_copied))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GroupCard(
            items = SupportMethod.entries.map { method ->
                {
                    SupportRow(method) {
                        when (method.action) {
                            SupportAction.OPEN -> runCatching { uriHandler.openUri(method.value) }
                            SupportAction.COPY -> copy(method.value)
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SupportRow(method: SupportMethod, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceIcon(method.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(method.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(method.action.icon, contentDescription = null)
    }
}
