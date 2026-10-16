package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.redactSensitiveData
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

sealed interface ServerError {
    data object InvalidLink : ServerError
    data object EmptySubscription : ServerError
    data class FetchFailed(val reason: String) : ServerError
}

data class ServersUiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val selected: ProfileEntity? = null,
    val delays: Map<Long, ProxyDelay> = emptyMap(),
    val testingIds: Set<Long> = emptySet(),
    val error: ServerError? = null
) {
    val testing: Boolean get() = testingIds.isNotEmpty()
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val latencyProbe: ServerLatencyProbe,
    private val log: AppLog
) : ViewModel() {

    private val _error = MutableStateFlow<ServerError?>(null)
    private val _delays = MutableStateFlow<Map<Long, ProxyDelay>>(emptyMap())
    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ServersUiState> = combine(
        profileRepository.profiles,
        profileRepository.selected,
        _delays,
        _testingIds,
        _error
    ) { profiles, selected, delays, testingIds, error ->
        ServersUiState(profiles, selected, delays, testingIds, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServersUiState())

    fun testAll() {
        if (_testingIds.value.isNotEmpty()) return
        val ids = uiState.value.profiles.map { it.id }.toSet()
        if (ids.isEmpty()) return
        _delays.value -= ids
        _testingIds.value = ids
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val delays = latencyProbe.measureAll()
                _delays.value = delays
                profileRepository.reorder(uiState.value.profiles.sortedBy { delays[it.id].rank() }.map { it.id })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                log.w(TAG, "latency test failed", t)
                _delays.value = ids.associateWith { ProxyDelay.Failed }
            } finally {
                _testingIds.value = emptySet()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun add(raw: String) {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult("add server", profileRepository.add(raw))
        }
    }

    fun readConfigFile(open: () -> InputStream?, onRead: (String?) -> Unit) {
        viewModelScope.launch {
            onRead(withContext(Dispatchers.IO) { profileRepository.readConfigFile(open) })
        }
    }

    fun update(profile: ProfileEntity, name: String, link: String) {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult("edit server", profileRepository.update(profile, name, link))
        }
    }

    fun refreshSubscription(profile: ProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult("subscription refresh", profileRepository.refreshSubscription(profile))
        }
    }

    fun select(profile: ProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.select(profile) }
    }

    fun delete(profiles: List<ProfileEntity>) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.delete(profiles) }
    }

    private fun handleResult(operation: String, result: ProfileOperationResult) {
        when (result) {
            is ProfileOperationResult.Success -> {
                if (result.affected > 1) log.i(TAG, "$operation: ${result.affected} servers")
            }
            ProfileOperationResult.InvalidLink -> {
                log.w(TAG, "$operation: unsupported or invalid link")
                _error.value = ServerError.InvalidLink
            }
            ProfileOperationResult.EmptySubscription -> {
                log.w(TAG, "$operation: subscription returned no servers")
                _error.value = ServerError.EmptySubscription
            }
            is ProfileOperationResult.FetchFailed -> {
                log.w(TAG, "$operation: subscription fetch failed", result.cause)
                _error.value = ServerError.FetchFailed(redactSensitiveData(result.cause.message ?: "unknown error"))
            }
        }
    }

    private companion object {
        const val TAG = "Servers"

        fun ProxyDelay?.rank(): Int = when (this) {
            is ProxyDelay.Measured -> ms
            null -> Int.MAX_VALUE - 1
            ProxyDelay.Failed -> Int.MAX_VALUE
        }
    }
}
