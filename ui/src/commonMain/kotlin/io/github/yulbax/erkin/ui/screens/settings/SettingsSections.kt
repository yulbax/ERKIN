package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.res.local_network_denied
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
import io.github.yulbax.erkin.ui.viewmodel.UpdateCheckResult
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import io.github.yulbax.erkin.ui.platform.rememberFilePicker
import io.github.yulbax.erkin.ui.platform.rememberLocalNetworkRequest
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
internal fun ServersSection(ui: SettingsUiState, viewModel: SettingsViewModel) {
    GroupCard(
        title = stringResource(Res.string.servers_title),
        items = listOf {
            SwitchRow(stringResource(Res.string.sort_servers_by_ping), ui.sortServersByPing) {
                viewModel.setSortServersByPing(it)
            }
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
    val showMessage = rememberShowMessage()
    val localNetworkDenied = stringResource(Res.string.local_network_denied)
    val invalidRemoteDns = stringResource(Res.string.invalid_remote_dns)
    val invalidByeDpiDns = stringResource(Res.string.invalid_byedpi_dns)
    val requestLocalNetwork = rememberLocalNetworkRequest { granted ->
        if (granted) viewModel.setBypassLan(true) else showMessage(localNetworkDenied)
    }

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
                    onCommit = { if (it.isNotBlank() && !viewModel.setDnsRemote(it)) showMessage(invalidRemoteDns) }
                )
            },
            {
                CommittedTextField(
                    label = stringResource(Res.string.byedpi_dns),
                    saved = network.byeDpiDns.address,
                    onCommit = { if (it.isNotBlank() && !viewModel.setByeDpiDnsAddress(it)) showMessage(invalidByeDpiDns) }
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
                SwitchRow(stringResource(Res.string.byedpi_dns_encrypted), network.byeDpiDns.transport.encrypted) {
                    viewModel.setByeDpiDnsEncrypted(it)
                }
            },
            {
                SwitchRow(stringResource(Res.string.sniff_destination), network.sniff) { viewModel.setSniff(it) }
            },
            {
                SwitchRow(stringResource(Res.string.block_quic), network.blockQuic) { viewModel.setBlockQuic(it) }
            },
            {
                SwitchRow(stringResource(Res.string.bypass_lan), network.bypassLan) { enabled ->
                    if (enabled) requestLocalNetwork() else viewModel.setBypassLan(false)
                }
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
            rulesAvailable = true,
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
            rulesAvailable = backup.rules != null,
            onConfirm = { selection ->
                importPreview = null
                viewModel.applyImport(backup, selection) { result ->
                    val message = if (result.error != null) {
                        importFailedFormat.format(result.error)
                    } else {
                        importDoneFormat.format(result.applied, result.deferred, result.profilesAdded, result.rulesAdded)
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
    rulesAvailable: Boolean,
    onConfirm: (BackupSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var settings by remember { mutableStateOf(settingsAvailable) }
    var apps by remember { mutableStateOf(appsAvailable) }
    var profiles by remember { mutableStateOf(profilesAvailable) }
    var rules by remember { mutableStateOf(rulesAvailable) }

    AppDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                enabled = settings || apps || profiles || rules,
                onClick = { onConfirm(BackupSelection(settings, apps, profiles, rules)) }
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
                    add { CheckRow(stringResource(Res.string.backup_servers), profiles) { profiles = it } }
                }
                if (rulesAvailable) {
                    add { CheckRow(stringResource(Res.string.rules_title), rules) { rules = it } }
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

@Composable
internal fun UpdatesSection(updates: UpdateViewModel) {
    val checking by updates.checking.collectAsStateWithLifecycle()
    val showMessage = rememberShowMessage()
    val upToDate = stringResource(Res.string.update_up_to_date)
    val failed = stringResource(Res.string.update_check_failed)

    GroupCard(
        title = stringResource(Res.string.updates_title),
        items = listOf {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(enabled = !checking) {
                        updates.checkNow { result ->
                            when (result) {
                                UpdateCheckResult.UP_TO_DATE -> showMessage(upToDate)
                                UpdateCheckResult.FAILED -> showMessage(failed)
                                UpdateCheckResult.AVAILABLE -> Unit
                            }
                        }
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.update_check), style = MaterialTheme.typography.bodyLarge)
                    updates.currentVersion?.let {
                        Text(
                            text = stringResource(Res.string.update_current, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }
        }
    )
}
