package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.new_apps_channel
import io.github.yulbax.erkin.ui.screens.apps.AppRowConnectedGroup
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NewAppsChannelRow(selected: ConnectionType, onSelect: (ConnectionType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.new_apps_channel),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        AppRowConnectedGroup(selected = selected, vpnEnabled = true, onSelect = onSelect)
    }
}
