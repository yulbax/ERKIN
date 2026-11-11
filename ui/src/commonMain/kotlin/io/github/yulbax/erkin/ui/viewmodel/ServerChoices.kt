package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.vpn.ProxyRef
import io.github.yulbax.erkin.vpn.VpnStateRepository
import io.github.yulbax.erkin.vpn.core.ProbeResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun ViewModel.serverChoicesOf(profiles: ProfileRepository, states: VpnStateRepository, results: ProbeResults): StateFlow<ServerChoices> =
    profiles.serverChoices(states, results).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ServerChoices())

private fun ProfileRepository.serverChoices(states: VpnStateRepository, results: ProbeResults): Flow<ServerChoices> =
    combine(
        profiles,
        groups,
        states.stats.map { it.unreachableServers }.distinctUntilChanged(),
        results.results
    ) { servers, groups, down, resultMap ->
        ServerChoices(servers, groups.associateBy { it.id }, down, resultMap)
    }

data class ServerLabel(val name: String, val isChain: Boolean, val unreachable: Boolean = false)

sealed interface CurrentServer {
    data object None : CurrentServer
    data object Main : CurrentServer
    data object ByeDpi : CurrentServer
    data class Bound(val serverId: Long) : CurrentServer
}

class ServerChoices(
    val servers: List<ProfileEntity> = emptyList(),
    val groups: Map<Long, ServerGroupEntity> = emptyMap(),
    private val unreachable: Set<Long> = emptySet(),
    private val results: Map<Long, ProbeResult> = emptyMap()
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

    fun groupOf(serverId: Long): Long? = byId[serverId]?.groupId

    fun resultOf(id: Long): ProbeResult? = results[id]

    fun linksOnly(): ServerChoices = ServerChoices(servers.filter { it.isLink }, groups, unreachable, results)

    private fun labelOf(server: ProfileEntity): ServerLabel = when (val kind = server.kind) {
        is ServerKind.Link -> ServerLabel(server.name, isChain = false)
        is ServerKind.Chain -> ServerLabel(
            name = kind.chain?.let { byId[it.exitId] }?.name?.ifEmpty { null } ?: server.name,
            isChain = true
        )
    }
}

enum class PickerLead { MAIN_SERVER, BYEDPI, NONE }

sealed interface ServerPickerRow {
    val key: Any

    data class Main(val name: String, val checked: Boolean) : ServerPickerRow {
        override val key: Any get() = "main"
    }

    data class ByeDpi(val checked: Boolean) : ServerPickerRow {
        override val key: Any get() = "byedpi"
    }

    data class Group(val id: Long, val kind: GroupKind, val name: String, val count: Int, val current: String?) : ServerPickerRow {
        override val key: Any get() = "group:$id"
    }

    data class Server(val id: Long, val name: String, val checked: Boolean, val group: String? = null, val result: ProbeResult? = null) : ServerPickerRow {
        override val key: Any get() = id
    }
}

data class ServerPickerPage(val openGroup: Long?, val group: String?, val rows: List<ServerPickerRow>)

internal fun serverPickerPage(
    choices: ServerChoices,
    query: String,
    openGroup: Long?,
    current: CurrentServer,
    lead: PickerLead = PickerLead.MAIN_SERVER
): ServerPickerPage {
    fun row(server: ProfileEntity, group: String? = null) = ServerPickerRow.Server(
        id = server.id,
        name = server.name,
        checked = current == CurrentServer.Bound(server.id),
        group = group,
        result = choices.resultOf(server.id)
    )
    val items = serverListItems(choices.servers, choices.groups)
    val groups = items.filterIsInstance<ServerListItem.Group>()
    val needle = query.trim()
    if (needle.isNotEmpty()) {
        val groupNames = groups.flatMap { group -> group.members.map { it.id to group.name } }.toMap()
        val found = choices.servers.filter { it.name.contains(needle, ignoreCase = true) }
        return ServerPickerPage(openGroup, group = null, rows = found.map { row(it, groupNames[it.id]) })
    }
    groups.firstOrNull { it.id == openGroup }?.let { group ->
        return ServerPickerPage(openGroup, group = group.name, rows = group.members.map { row(it) })
    }
    val leading = when (lead) {
        PickerLead.MAIN_SERVER -> choices.main?.let { ServerPickerRow.Main(it.name, checked = current == CurrentServer.Main) }
        PickerLead.BYEDPI -> ServerPickerRow.ByeDpi(checked = current == CurrentServer.ByeDpi)
        PickerLead.NONE -> null
    }
    val rows = items.map { item ->
        when (item) {
            is ServerListItem.Server -> row(item.profile)
            is ServerListItem.Group -> ServerPickerRow.Group(
                id = item.id,
                kind = item.kind,
                name = item.name,
                count = item.members.size,
                current = item.members.firstOrNull { current == CurrentServer.Bound(it.id) }?.name
            )
        }
    }
    return ServerPickerPage(openGroup, group = null, rows = listOfNotNull(leading) + rows)
}

private const val STOP_TIMEOUT_MS = 5_000L
