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
import io.github.yulbax.erkin.vpn.core.ByeDpiDns
import io.github.yulbax.erkin.vpn.core.DnsTransport
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.RemoteDns
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import io.github.yulbax.erkin.data.profile.ServerSort
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.HomeCardPosition

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: ConfigBackupRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000.milliseconds), SettingsEntity().toUiState())

    fun toggleShowSystemApps() = update { it.copy(showSystemApps = !it.showSystemApps) }
    fun setAutoConnect(value: Boolean) = update { it.copy(autoConnect = value) }
    fun setServerSort(value: ServerSort) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(serverSort = value.wire) }
            profileRepository.applySort()
        }
    }
    fun setHomeCardPosition(value: HomeCardPosition) = update { it.copy(homeCardPosition = value.wire) }
    fun setNewAppsConnectionType(value: ConnectionType) = update { it.copy(newAppsConnectionType = value.wire) }

    fun setMtu(value: Int) = updateNetwork { it.copy(mtu = value.coerceIn(NetworkOptions.MTU_RANGE)) }
    fun setIpv6Mode(value: Ipv6Mode) = updateNetwork { it.copy(ipv6Mode = value) }
    fun setDnsRemote(value: String): Boolean {
        if (!RemoteDns.isValid(value)) return false
        updateNetwork { it.copy(dnsRemote = value.trim()) }
        return true
    }
    fun setDnsDirect(value: String) = updateNetwork { it.copy(dnsDirect = value.trim()) }
    fun setByeDpiDnsAddress(value: String): Boolean {
        val address = ByeDpiDns.normalizeAddress(value) ?: return false
        updateNetwork { it.copy(byeDpiDns = it.byeDpiDns.copy(address = address)) }
        return true
    }
    fun setByeDpiDnsEncrypted(value: Boolean) =
        updateNetwork { it.copy(byeDpiDns = it.byeDpiDns.copy(transport = DnsTransport.of(value))) }
    fun setSniff(value: Boolean) = updateNetwork { it.copy(sniff = value) }
    fun setBypassLan(value: Boolean) = updateNetwork { it.copy(bypassLan = value) }
    fun setBlockQuic(value: Boolean) = updateNetwork { it.copy(blockQuic = value) }
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
    val autoConnect: Boolean = false,
    val serverSort: ServerSort = ServerSort.DEFAULT,
    val homeCardPosition: HomeCardPosition = HomeCardPosition.DEFAULT,
    val newAppsConnectionType: ConnectionType = ConnectionType.DIRECT,
    val network: NetworkOptions = NetworkOptions.DEFAULT
)

private fun SettingsEntity.toUiState() = SettingsUiState(
    showSystemApps = showSystemApps,
    autoConnect = autoConnect,
    serverSort = serverSort(),
    homeCardPosition = homeCardPosition(),
    newAppsConnectionType = newAppsConnectionType(platformNewAppsConnectionType),
    network = networkOptions()
)
