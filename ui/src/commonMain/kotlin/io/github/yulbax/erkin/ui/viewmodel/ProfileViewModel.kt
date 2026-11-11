package io.github.yulbax.erkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.AddTarget
import io.github.yulbax.erkin.data.profile.GroupTarget
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.SubscriptionRefusal
import io.github.yulbax.erkin.proxy.protocol.OpenVpn
import io.github.yulbax.erkin.vpn.CoreEvent
import io.github.yulbax.erkin.vpn.CoreLog
import kotlinx.coroutines.flow.filterIsInstance
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.server_group_default
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import org.jetbrains.compose.resources.getString

sealed interface LoginRequest {
    class NewServer(val raw: String, val fileName: String?, val groupId: Long?) : LoginRequest

    class Existing(val profile: ProfileEntity, val rejected: Boolean) : LoginRequest {
        val username: String get() = OpenVpn.username(profile.link).orEmpty()
    }
}

sealed interface ServerError {
    data object InvalidLink : ServerError
    data object EmptySubscription : ServerError
    data class FetchFailed(val reason: String) : ServerError
    data class Refused(val refusal: SubscriptionRefusal) : ServerError
}

fun List<ProfileEntity>.orderByDelay(ids: Set<Long>, delays: Map<Long, ProxyDelay>, groups: Map<Long, ServerGroupEntity>): List<Long> =
    serverListItems(sortedByDelay(ids, delays) { it }, groups)
        .sortedByDelay(ids, delays) { it.server }
        .flatMap { it.profileIds }

private fun <T> List<T>.sortedByDelay(ids: Set<Long>, delays: Map<Long, ProxyDelay>, server: (T) -> ProfileEntity): List<T> {
    val tested: (T) -> Boolean = { server(it).id in ids }
    val ranked = filter(tested).sortedBy { delays[server(it).id].rank() }.iterator()
    return map { if (tested(it)) ranked.next() else it }
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
    val groups: Map<Long, ServerGroupEntity> = emptyMap(),
    val refreshing: Set<Long> = emptySet(),
    val items: List<ServerListItem> = emptyList(),
    val error: ServerError? = null
) {
    val testing: Boolean get() = testingIds.isNotEmpty()

    fun group(id: Long): ServerListItem.Group? =
        items.firstOrNull { it is ServerListItem.Group && it.id == id } as ServerListItem.Group?
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val latencyProbe: ServerLatencyProbe,
    private val serverDelays: ServerDelays,
    private val log: AppLog,
    private val coreLog: CoreLog
) : ViewModel() {

    private val _error = MutableStateFlow<ServerError?>(null)
    private val _login = MutableStateFlow<LoginRequest?>(null)
    val login: StateFlow<LoginRequest?> = _login
    private val questioned = HashSet<Long>()
    private val _testingIds = MutableStateFlow<Set<Long>>(emptySet())
    private var running: Deferred<List<Long>?>? = null
    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    private val _unreachable = MutableSharedFlow<List<Long>>(extraBufferCapacity = 1)
    val unreachable: SharedFlow<List<Long>> = _unreachable

    private val groups = combine(profileRepository.groups, _refreshing) { stored, refreshing ->
        stored.associateBy { it.id } to refreshing
    }

    val uiState: StateFlow<ServersUiState> = combine(
        profileRepository.profiles,
        profileRepository.selected,
        combine(serverDelays.delays, _testingIds) { delays, testingIds -> delays to testingIds },
        groups,
        _error
    ) { profiles, selected, (delays, testingIds), (stored, refreshing), error ->
        ServersUiState(
            profiles = profiles,
            selected = selected,
            delays = delays,
            testingIds = testingIds,
            groups = stored,
            refreshing = refreshing,
            items = serverListItems(profiles, stored),
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServersUiState())

    fun test(servers: List<Long>): Deferred<List<Long>?> {
        val ids = servers.toSet()
        if (running?.isActive == true || ids.isEmpty()) return CompletableDeferred(null)
        serverDelays.update { it - ids }
        _testingIds.value = ids
        return viewModelScope.async(Dispatchers.IO) {
            val delays = mutableMapOf<Long, ProxyDelay>()
            try {
                latencyProbe.measureEach(uiState.value.items.testQueue().filter { it in ids }).collect { (id, delay) ->
                    delays[id] = delay
                    serverDelays.update { it + (id to delay) }
                    _testingIds.update { it - id }
                }
                val order = if (settingsRepository.settings.first().sortServersByPing) {
                    val state = uiState.value
                    state.profiles.orderByDelay(ids, delays, state.groups).also { profileRepository.reorder(it) }
                } else {
                    null
                }
                ids.filter { delays[it] == ProxyDelay.Failed }.takeIf { it.isNotEmpty() }?.let(_unreachable::tryEmit)
                order
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                log.w(TAG, "latency test failed", t)
                serverDelays.update { it + (ids - delays.keys).associateWith { ProxyDelay.Failed } }
                null
            } finally {
                _testingIds.value = emptySet()
            }
        }.also { running = it }
    }

    init {
        viewModelScope.launch { coreLog.all.filterIsInstance<CoreEvent.LoginRejected>().collect { askIfRejected(it.serverId) } }
    }

    fun clearError() {
        _error.value = null
    }

    fun add(raw: String, fileName: String? = null, groupId: Long? = null) {
        if (OpenVpn.asksForLogin(raw.trim())) {
            _login.value = LoginRequest.NewServer(raw, fileName, groupId)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val target = groupId?.let(AddTarget::Group) ?: AddTarget.Main(newGroupName())
            handleResult("add server", profileRepository.add(raw, fileName, target))
        }
    }

    fun editLogin(profile: ProfileEntity) {
        _login.value = LoginRequest.Existing(profile, rejected = false)
    }

    fun submitLogin(username: String, password: String) {
        val request = _login.value ?: return
        _login.value = null
        when (request) {
            is LoginRequest.NewServer ->
                add(OpenVpn.withLogin(request.raw, username.trim(), password), request.fileName, request.groupId)
            is LoginRequest.Existing -> {
                questioned -= request.profile.id
                update(request.profile, request.profile.name, OpenVpn.withLogin(request.profile.link, username.trim(), password))
            }
        }
    }

    private suspend fun askIfRejected(id: Long) {
        if (_login.value != null || !questioned.add(id)) return
        if (latencyProbe.measure(setOf(id))[id] is ProxyDelay.Measured) return
        val profile = profileRepository.profiles.first().firstOrNull { it.id == id } ?: return
        if (_login.value == null) _login.value = LoginRequest.Existing(profile, rejected = true)
    }

    fun cancelLogin() {
        _login.value = null
    }

    fun moveToNewGroup(ids: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.moveToGroup(ids, GroupTarget.New(newGroupName())) }
    }

    fun moveToGroup(ids: Collection<Long>, groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.moveToGroup(ids, GroupTarget.Existing(groupId)) }
    }

    fun removeFromGroup(ids: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.removeFromGroup(ids) }
    }

    fun dissolveGroup(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.dissolveGroup(id) }
    }

    private suspend fun newGroupName(): String {
        val taken = profileRepository.groups.first().mapNotNullTo(HashSet()) { it.name }
        var number = 1
        while (true) {
            val name = getString(Res.string.server_group_default, number)
            if (name !in taken) return name
            number++
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

    fun renameGroup(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) { profileRepository.renameGroup(id, name) }
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
