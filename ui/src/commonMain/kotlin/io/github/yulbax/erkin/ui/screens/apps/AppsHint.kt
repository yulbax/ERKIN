package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.components.HintCard
import io.github.yulbax.erkin.ui.platform.appsCanBypass
import io.github.yulbax.erkin.ui.platform.platformChannels
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.ConnectionTypeIcon
import io.github.yulbax.erkin.ui.components.ui

@Composable
internal fun AppsHint(onDismiss: () -> Unit) {
    HintCard(
        onDismiss = onDismiss,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        actionLabel = stringResource(Res.string.got_it),
        onAction = onDismiss,
        headerAlignment = Alignment.CenterVertically,
        header = {
            Text(
                text = stringResource(Res.string.apps_hint_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Spacer(Modifier.height(8.dp))
        platformChannels.forEach { TypeLine(it, stringResource(it.ui.hint)) }
        if (appsCanBypass) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.apps_hint_ru_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TypeLine(type: ConnectionType, description: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectionTypeIcon(type, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
