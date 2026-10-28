package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.runtime.Composable
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.components.DropdownSetting
import io.github.yulbax.erkin.ui.components.label
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.new_apps_channel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NewAppsChannelRow(selected: ConnectionType, onSelect: (ConnectionType) -> Unit) {
    val labels = ConnectionType.entries.associateWith { it.label() }
    DropdownSetting(
        label = stringResource(Res.string.new_apps_channel),
        options = ConnectionType.entries,
        selected = selected,
        optionLabel = labels::getValue,
        onSelect = onSelect
    )
}
