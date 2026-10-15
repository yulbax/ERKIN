package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.ui.platform.platformNewAppsConnectionType
import io.github.yulbax.erkin.data.ConnectionType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.AppConfigBackup
import io.github.yulbax.erkin.data.BackupSelection
import io.github.yulbax.erkin.data.ConfigBackupRepository
import io.github.yulbax.erkin.data.ImportResult
import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: ConfigBackupRepository
) : ViewModel() {

    val byeDpiArgsDefault: String = ByeDpiArgs.DEFAULT.joinToString(" ")

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity().toUiState())

    fun toggleShowSystemApps() = update { it.copy(showSystemApps = !it.showSystemApps) }
    fun setByeDpiArgs(args: String) = update { it.copy(byeDpiArgs = args) }
    fun setAutoConnect(value: Boolean) = update { it.copy(autoConnect = value) }
    fun setNewAppsConnectionType(value: ConnectionType) = update { it.copy(newAppsConnectionType = value.wire) }

    fun setMtu(value: Int) = updateNetwork { it.copy(mtu = value.coerceIn(NetworkOptions.MTU_RANGE)) }
    fun setIpv6Mode(value: Ipv6Mode) = updateNetwork { it.copy(ipv6Mode = value) }
    fun setDnsRemote(value: String) = updateNetwork { it.copy(dnsRemote = value.trim()) }
    fun setDnsDirect(value: String) = updateNetwork { it.copy(dnsDirect = value.trim()) }
    fun setSniff(value: Boolean) = updateNetwork { it.copy(sniff = value) }
    fun setBypassLan(value: Boolean) = updateNetwork { it.copy(bypassLan = value) }
    fun setPreferredFingerprint(value: TlsFingerprint?) = updateNetwork { it.copy(preferredFingerprint = value) }

    fun exportConfig(selection: BackupSelection, onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(backupRepository.export(selection))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun prepareImport(open: () -> InputStream?, onParsed: (AppConfigBackup?) -> Unit) {
        viewModelScope.launch {
            onParsed(backupRepository.inspect(open))
        }
    }

    fun applyImport(
        backup: AppConfigBackup,
        selection: BackupSelection,
        onResult: (ImportResult) -> Unit
    ) {
        viewModelScope.launch {
            onResult(backupRepository.apply(backup, selection))
        }
    }

    private fun updateNetwork(transform: (NetworkOptions) -> NetworkOptions) = update {
        it.withNetworkOptions(transform(it.networkOptions()))
    }

    private fun update(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }
}

data class SettingsUiState(
    val showSystemApps: Boolean = false,
    val byeDpiArgs: String = "",
    val autoConnect: Boolean = false,
    val newAppsConnectionType: ConnectionType = ConnectionType.DIRECT,
    val network: NetworkOptions = NetworkOptions.DEFAULT
)

private fun SettingsEntity.toUiState() = SettingsUiState(
    showSystemApps = showSystemApps,
    byeDpiArgs = byeDpiArgs,
    autoConnect = autoConnect,
    newAppsConnectionType = newAppsConnectionType(platformNewAppsConnectionType),
    network = networkOptions()
)
