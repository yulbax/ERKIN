package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.geoip.GeoIpState
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.viewmodel.SettingsViewModel
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GeoIpSection(viewModel: SettingsViewModel) {
    val state by viewModel.geoIp.collectAsStateWithLifecycle()
    val showMessage = rememberShowMessage()
    val failedFormat = stringResource(Res.string.geoip_failed)
    LaunchedEffect(Unit) { viewModel.refreshGeoIp() }

    GroupCard(
        title = stringResource(Res.string.geoip_title),
        description = stringResource(Res.string.geoip_description),
        items = listOf {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.geoip_database), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when (val current = state) {
                            GeoIpState.Missing -> stringResource(Res.string.geoip_missing)
                            is GeoIpState.Ready -> stringResource(
                                Res.string.geoip_updated,
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(current.updatedAt))
                            )
                            is GeoIpState.Downloading -> current.progress
                                ?.let { stringResource(Res.string.geoip_downloading_percent, "${(it * 100).toInt()}%") }
                                ?: stringResource(Res.string.geoip_downloading)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (state is GeoIpState.Downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    FilledTonalButton(
                        onClick = {
                            viewModel.downloadGeoIp { error ->
                                if (error != null) showMessage(failedFormat.format(error.message ?: error::class.simpleName))
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (state is GeoIpState.Ready) Res.string.geoip_update else Res.string.geoip_download
                            )
                        )
                    }
                }
            }
        }
    )
}
