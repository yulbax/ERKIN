package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.vpn.ProxyRef
import io.github.yulbax.erkin.vpn.VpnStateRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun ViewModel.serverChoicesOf(profiles: ProfileRepository, states: VpnStateRepository): StateFlow<ServerChoices> =
    profiles.serverChoices(states).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ServerChoices())

private fun ProfileRepository.serverChoices(states: VpnStateRepository): Flow<ServerChoices> =
    combine(profiles, subscriptions, states.stats.map { it.unreachableServers }.distinctUntilChanged()) { servers, subscriptions, down ->
        ServerChoices(servers, subscriptions.associateBy { it.id }, down)
    }

data class ServerLabel(val name: String, val isChain: Boolean, val unreachable: Boolean = false)

sealed interface CurrentServer {
    data object None : CurrentServer
    data object Main : CurrentServer
    data class Bound(val serverId: Long) : CurrentServer
}

class ServerChoices(
    val servers: List<ProfileEntity> = emptyList(),
    val subscriptions: Map<Long, SubscriptionEntity> = emptyMap(),
    private val unreachable: Set<Long> = emptySet()
) {
    private val byId = servers.associateBy { it.id }
    private val labels = servers.associate { it.id to labelOf(it) }

    val main: ProfileEntity? = servers.firstOrNull { it.selected }
    val vpnEnabled: Boolean = servers.isNotEmpty()
    val canChoose: Boolean = servers.size > 1

    fun current(channel: ConnectionType, serverId: Long?): CurrentServer = when {
        channel != ConnectionType.VPN -> CurrentServer.None
        else -> byId[serverId]?.let { CurrentServer.Bound(it.id) } ?: CurrentServer.Main
    }

    fun current(app: AppInfo): CurrentServer = current(app.connectionType, app.serverId)

    fun label(channel: ConnectionType, serverId: Long?): ServerLabel? =
        (current(channel, serverId) as? CurrentServer.Bound)?.let { bound ->
            labels[bound.serverId]?.copy(unreachable = bound.serverId in unreachable)
        }

    fun labelFor(app: AppInfo): ServerLabel? = label(app.connectionType, app.serverId)

    fun labelOf(server: ProxyRef): ServerLabel? {
        val id = when (server) {
            is ProxyRef.Profile -> server.id
            is ProxyRef.Hop -> hopServer(server) ?: return null
        }
        return labels[id]?.copy(unreachable = id in unreachable)
    }

    private fun hopServer(hop: ProxyRef.Hop): Long? {
        val chain = (byId[hop.chainId]?.kind as? ServerKind.Chain)?.chain ?: return null
        return (chain.hops.getOrNull(hop.index) as? ChainEntry.Server)?.profileId
    }

    fun subscriptionOf(serverId: Long): Long? = byId[serverId]?.subscriptionId

    private fun labelOf(server: ProfileEntity): ServerLabel = when (val kind = server.kind) {
        is ServerKind.Link -> ServerLabel(server.name, isChain = false)
        is ServerKind.Chain -> ServerLabel(
            name = kind.chain?.let { byId[it.exitId] }?.name?.ifEmpty { null } ?: server.name,
            isChain = true
        )
    }
}

sealed interface ServerPickerRow {
    val key: Any

    data class Main(val name: String, val checked: Boolean) : ServerPickerRow {
        override val key: Any get() = "main"
    }

    data class Subscription(val id: Long, val name: String, val count: Int, val open: Boolean) : ServerPickerRow {
        override val key: Any get() = "subscription:$id"
    }

    data class Server(val id: Long, val name: String, val checked: Boolean, val indented: Boolean = false) : ServerPickerRow {
        override val key: Any get() = id
    }
}

internal fun serverPickerRows(
    choices: ServerChoices,
    query: String,
    expanded: Set<Long>,
    current: CurrentServer
): List<ServerPickerRow> {
    fun row(server: ProfileEntity, indented: Boolean = false) = ServerPickerRow.Server(
        id = server.id,
        name = server.name,
        checked = current == CurrentServer.Bound(server.id),
        indented = indented
    )
    val needle = query.trim()
    if (needle.isNotEmpty()) {
        return choices.servers.filter { it.name.contains(needle, ignoreCase = true) }.map { row(it) }
    }
    val main = choices.main?.let { ServerPickerRow.Main(it.name, checked = current == CurrentServer.Main) }
    return listOfNotNull(main) + serverListItems(choices.servers, choices.main?.id, choices.subscriptions).flatMap { item ->
        when (item) {
            is ServerListItem.Server -> listOf(row(item.profile))
            is ServerListItem.Subscription -> {
                val open = item.id in expanded
                val members = if (open) item.members.map { row(it, indented = true) } else emptyList()
                listOf(ServerPickerRow.Subscription(item.id, item.name, item.members.size, open)) + members
            }
        }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
