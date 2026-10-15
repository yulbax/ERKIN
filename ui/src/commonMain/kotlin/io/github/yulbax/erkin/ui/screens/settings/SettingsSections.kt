package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.AppConfigBackup
import io.github.yulbax.erkin.data.BackupSelection
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.ClickableRow
import io.github.yulbax.erkin.ui.components.CommittedTextField
import io.github.yulbax.erkin.ui.components.DropdownSetting
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.SwitchRow
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.viewmodel.SettingsUiState
import io.github.yulbax.erkin.ui.viewmodel.SettingsViewModel
import io.github.yulbax.erkin.ui.platform.rememberFilePicker
import io.github.yulbax.erkin.ui.platform.rememberSharer
import io.github.yulbax.erkin.ui.platform.rememberShowMessage
import io.github.yulbax.erkin.ui.platform.rememberSystemVpnSettings
import kotlinx.coroutines.launch
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.TlsFingerprint

@Composable
internal fun ByeDpiSection(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val savedByeDpiArgs = ui.byeDpiArgs
    var text by remember { mutableStateOf(savedByeDpiArgs) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(savedByeDpiArgs) { if (!focused) text = savedByeDpiArgs }

    GroupCard(
        title = stringResource(Res.string.byedpi_cli_args),
        action = {
            Text(
                text = stringResource(Res.string.reset_to_default),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    text = ""
                    viewModel.setByeDpiArgs("")
                }
            )
        },
        items = listOf {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = {
                    Text(viewModel.byeDpiArgsDefault, fontFamily = FontFamily.Monospace)
                },
                label = { Text(stringResource(Res.string.flags_label)) },
                colors = transparentFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { st ->
                        if (focused && !st.isFocused) viewModel.setByeDpiArgs(text.trim())
                        focused = st.isFocused
                    }
            )
        }
    )
}

@Composable
internal fun AutostartSection(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val openSystemVpnSettings = rememberSystemVpnSettings()
    val autoConnect = ui.autoConnect
    val alwaysOnLabel = stringResource(Res.string.always_on_vpn)

    GroupCard(
        title = stringResource(Res.string.autostart_title),
        items = buildList {
            add {
                SwitchRow(stringResource(Res.string.auto_connect), autoConnect) {
                    viewModel.setAutoConnect(it)
                }
            }
            if (openSystemVpnSettings != null) {
                add { ClickableRow(label = alwaysOnLabel, onClick = openSystemVpnSettings) }
            }
        }
    )
}

@Composable
internal fun NetworkSection(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val network = ui.network
    val fromConfigLabel = stringResource(Res.string.fingerprint_from_config)

    GroupCard(
        title = stringResource(Res.string.network_title),
        items = listOfNotNull(
            {
                DropdownSetting(
                    label = stringResource(Res.string.tls_fingerprint),
                    options = listOf<TlsFingerprint?>(null) + TlsFingerprint.entries,
                    selected = network.preferredFingerprint,
                    optionLabel = { it?.wire ?: fromConfigLabel },
                    onSelect = viewModel::setPreferredFingerprint
                )
            },
            {
                CommittedTextField(
                    label = stringResource(Res.string.mtu),
                    saved = network.mtu.toString(),
                    keyboardType = KeyboardType.Number,
                    onCommit = { it.trim().toIntOrNull()?.let(viewModel::setMtu) }
                )
            },
            {
                DropdownSetting(
                    label = stringResource(Res.string.ipv6),
                    options = Ipv6Mode.entries,
                    selected = network.ipv6Mode,
                    optionLabel = Ipv6Mode::wire,
                    onSelect = viewModel::setIpv6Mode
                )
            },
            {
                CommittedTextField(
                    label = stringResource(Res.string.remote_dns),
                    saved = network.dnsRemote,
                    onCommit = { if (it.isNotBlank()) viewModel.setDnsRemote(it) }
                )
            },
            {
                CommittedTextField(
                    label = stringResource(Res.string.direct_dns),
                    saved = network.dnsDirect,
                    onCommit = { if (it.isNotBlank()) viewModel.setDnsDirect(it) }
                )
            },
            {
                SwitchRow(stringResource(Res.string.sniff_destination), network.sniff) { viewModel.setSniff(it) }
            },
            {
                SwitchRow(stringResource(Res.string.bypass_lan), network.bypassLan) { viewModel.setBypassLan(it) }
            }
        )
    )
}

@Composable
internal fun BackupSection(viewModel: SettingsViewModel) {
    val sharer = rememberSharer()
    val showMessage = rememberShowMessage()
    val scope = rememberCoroutineScope()
    val exportAppConfigLabel = stringResource(Res.string.export_app_config)
    val importFailedFormat = stringResource(Res.string.import_failed)
    val importDoneFormat = stringResource(Res.string.import_done)
    val invalidFileMessage = stringResource(Res.string.import_invalid_file)

    var showExportDialog by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<AppConfigBackup?>(null) }

    val pickImportFile = rememberFilePicker(listOf("application/json", "text/plain", "*/*")) { file ->
        viewModel.prepareImport(file::open) { backup ->
            if (backup == null) showMessage(invalidFileMessage) else importPreview = backup
        }
    }

    GroupCard(
        title = stringResource(Res.string.backup_title),
        items = listOf(
            {
                ClickableRow(
                    label = stringResource(Res.string.export_app_config),
                    trailingIcon = Icons.Filled.Upload
                ) {
                    showExportDialog = true
                }
            },
            {
                ClickableRow(
                    label = stringResource(Res.string.import_app_config),
                    trailingIcon = Icons.Filled.Download
                ) {
                    pickImportFile()
                }
            }
        )
    )

    if (showExportDialog) {
        BackupSelectionDialog(
            title = stringResource(Res.string.backup_export_title),
            confirmLabel = stringResource(Res.string.export),
            settingsAvailable = true,
            appsAvailable = true,
            profilesAvailable = true,
            onConfirm = { selection ->
                showExportDialog = false
                viewModel.exportConfig(selection) { content ->
                    scope.launch {
                        sharer.shareFile("erkin-config", "json", "application/json", content, exportAppConfigLabel)
                    }
                }
            },
            onDismiss = { showExportDialog = false }
        )
    }

    importPreview?.let { backup ->
        BackupSelectionDialog(
            title = stringResource(Res.string.backup_import_title),
            confirmLabel = stringResource(Res.string.ok),
            settingsAvailable = backup.settings != null,
            appsAvailable = backup.apps != null,
            profilesAvailable = backup.profiles != null,
            onConfirm = { selection ->
                importPreview = null
                viewModel.applyImport(backup, selection) { result ->
                    val message = if (result.error != null) {
                        importFailedFormat.format(result.error)
                    } else {
                        importDoneFormat.format(result.applied, result.skipped, result.profilesAdded)
                    }
                    showMessage(message)
                }
            },
            onDismiss = { importPreview = null }
        )
    }
}

@Composable
private fun BackupSelectionDialog(
    title: String,
    confirmLabel: String,
    settingsAvailable: Boolean,
    appsAvailable: Boolean,
    profilesAvailable: Boolean,
    onConfirm: (BackupSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember { mutableStateOf(settingsAvailable) }
    var apps by remember { mutableStateOf(appsAvailable) }
    var profiles by remember { mutableStateOf(profilesAvailable) }

    AppDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                enabled = settings || apps || profiles,
                onClick = { onConfirm(BackupSelection(settings, apps, profiles)) }
            ) { Text(confirmLabel) }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = buildList {
                if (settingsAvailable) {
                    add { CheckRow(stringResource(Res.string.settings), settings) { settings = it } }
                }
                if (appsAvailable) {
                    add { CheckRow(stringResource(Res.string.applications), apps) { apps = it } }
                }
                if (profilesAvailable) {
                    add { CheckRow(stringResource(Res.string.servers_title), profiles) { profiles = it } }
                }
            }
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
