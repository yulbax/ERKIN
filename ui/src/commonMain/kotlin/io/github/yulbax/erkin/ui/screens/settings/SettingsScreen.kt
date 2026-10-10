package io.github.yulbax.erkin.ui.screens.settings

import io.github.yulbax.erkin.ui.platform.supportsSystemAppsFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.SwitchRow
import io.github.yulbax.erkin.ui.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Settings(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        GroupCard(
            title = stringResource(Res.string.language),
            items = listOf { LanguagePicker() }
        )

        GroupCard(
            title = stringResource(Res.string.applications),
            items = listOfNotNull(
                {
                    NewAppsChannelRow(ui.newAppsConnectionType, viewModel::setNewAppsConnectionType)
                },
                if (supportsSystemAppsFilter) {
                    {
                        SwitchRow(stringResource(Res.string.show_system_apps), ui.showSystemApps) {
                            viewModel.toggleShowSystemApps()
                        }
                    }
                } else {
                    null
                }
            )
        )

        ByeDpiSection(ui, viewModel)
        AutostartSection(ui, viewModel)
        NetworkSection(ui, viewModel)
        BackupSection(viewModel)
    }
}
