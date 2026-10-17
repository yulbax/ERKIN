package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChainsViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val hintSeen: StateFlow<Boolean> = settingsRepository.settings
        .map { it.chainsHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissHint() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.update { it.copy(chainsHintSeen = true) }
        }
    }

    val profiles: StateFlow<List<ProfileEntity>> =
        profileRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(existing: ProfileEntity?, name: String, chain: ProxyChain, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { profileRepository.saveChain(existing, name, chain) })
        }
    }

    fun delete(chains: List<ProfileEntity>) {
        val targets = chains.filter { it.kind is ServerKind.Chain }
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { profileRepository.delete(targets) }
    }
}
