package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChainsViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> =
        profileRepository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(existing: ProfileEntity?, name: String, chain: ProxyChain, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { profileRepository.saveChain(existing, name, chain) })
        }
    }

    fun delete(chain: ProfileEntity) {
        if (chain.kind !is ServerKind.Chain) return
        viewModelScope.launch(Dispatchers.IO) { profileRepository.delete(listOf(chain)) }
    }
}
