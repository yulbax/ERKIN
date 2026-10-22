package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.isLink
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
    val subscriptions: Map<Long, SubscriptionEntity> = emptyMap(),
    val refreshing: Set<Long> = emptySet(),
    val items: List<ServerListItem> = emptyList(),
    val error: ServerError? = null
) {
    val testing: Boolean get() = testingIds.isNotEmpty()

    fun subscription(id: Long): ServerListItem.Subscription? =
        items.firstOrNull { it is ServerListItem.Subscription && it.id == id } as ServerListItem.Subscription?
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val latencyProbe: ServerLatencyProbe,
    private val log: AppLog
) : ViewModel() {

    private val _error = MutableStateFlow<ServerError?>(null)
    private val _delays = MutableStateFlow<Map<Long, ProxyDelay>>(emptyMap())
    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())

    private val subscriptions = combine(profileRepository.subscriptions, _refreshing) { stored, refreshing ->
        stored.associateBy { it.id } to refreshing
    }

    val uiState: StateFlow<ServersUiState> = combine(
        profileRepository.profiles,
        profileRepository.selected,
        combine(_delays, _testingIds) { delays, testingIds -> delays to testingIds },
        subscriptions,
        _error
    ) { profiles, selected, (delays, testingIds), (stored, refreshing), error ->
        ServersUiState(
            profiles = profiles,
            selected = selected,
            delays = delays,
            testingIds = testingIds,
            subscriptions = stored,
            refreshing = refreshing,
            items = serverListItems(profiles, selected?.id, stored),
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServersUiState())

    fun testAll() {
        if (_testingIds.value.isNotEmpty()) return
        val ids = uiState.value.profiles.filter { it.isLink }.map { it.id }.toSet()
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

    fun add(raw: String, fileName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            handleResult("add server", profileRepository.add(raw, fileName))
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

    fun refreshSubscription(id: Long) {
        if (id in _refreshing.value) return
        _refreshing.value += id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                handleResult("subscription refresh", profileRepository.refreshSubscription(id))
            } finally {
                _refreshing.value -= id
            }
        }
    }

    fun renameSubscription(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.renameSubscription(id, name) }
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
            is ProfileOperationResult.SubscriptionSynced ->
                log.i(TAG, "$operation: +${result.added} ~${result.updated} -${result.removed}")
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
