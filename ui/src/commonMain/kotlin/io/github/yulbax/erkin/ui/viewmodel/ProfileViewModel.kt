package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.SubscriptionRefusal
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.redactSensitiveData
import io.github.yulbax.erkin.vpn.ServerLatencyProbe
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

sealed interface ServerError {
    data object InvalidLink : ServerError
    data object EmptySubscription : ServerError
    data class FetchFailed(val reason: String) : ServerError
    data class Refused(val refusal: SubscriptionRefusal) : ServerError
}

fun List<ProfileEntity>.sortedByDelay(ids: Set<Long>, delays: Map<Long, ProxyDelay>): List<ProfileEntity> {
    val ranked = filter { it.id in ids }.sortedBy { delays[it.id].rank() }.iterator()
    return map { if (it.id in ids) ranked.next() else it }
}

private fun ProxyDelay?.rank(): Int = when (this) {
    is ProxyDelay.Measured -> ms
    null, ProxyDelay.NeedsConnection -> Int.MAX_VALUE - 1
    ProxyDelay.Failed -> Int.MAX_VALUE
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
    private val settingsRepository: SettingsRepository,
    private val latencyProbe: ServerLatencyProbe,
    private val log: AppLog
) : ViewModel() {

    private val _error = MutableStateFlow<ServerError?>(null)
    private val _delays = MutableStateFlow<Map<Long, ProxyDelay>>(emptyMap())
    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    private val _unreachable = MutableSharedFlow<List<Long>>(extraBufferCapacity = 1)
    val unreachable: SharedFlow<List<Long>> = _unreachable

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

    fun test(servers: List<Long>): Deferred<List<Long>?> {
        val ids = servers.toSet()
        if (_testingIds.value.isNotEmpty() || ids.isEmpty()) return CompletableDeferred(null)
        _delays.value -= ids
        _testingIds.value = ids
        return viewModelScope.async(Dispatchers.IO) {
            try {
                val delays = latencyProbe.measure(ids)
                _delays.value += delays
                val order = if (settingsRepository.settings.first().sortServersByPing) {
                    uiState.value.profiles.sortedByDelay(ids, delays).map { it.id }.also { profileRepository.reorder(it) }
                } else {
                    null
                }
                ids.filter { delays[it] == ProxyDelay.Failed }.takeIf { it.isNotEmpty() }?.let(_unreachable::tryEmit)
                order
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                log.w(TAG, "latency test failed", t)
                _delays.value = ids.associateWith { ProxyDelay.Failed }
                null
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

    fun reorder(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.reorder(ids) }
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
            is ProfileOperationResult.Refused -> {
                log.w(TAG, "$operation: subscription refused this device: ${result.refusal}")
                _error.value = ServerError.Refused(result.refusal)
            }
        }
    }

    private companion object {
        const val TAG = "Servers"
    }
}
