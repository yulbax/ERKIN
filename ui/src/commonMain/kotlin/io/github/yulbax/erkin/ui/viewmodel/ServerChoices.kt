package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.data.profile.ServerTree
import io.github.yulbax.erkin.data.profile.ServerFolder
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.vpn.ProbeResults
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
import kotlinx.coroutines.flow.WhileSubscribed
import kotlin.time.Duration.Companion.milliseconds

internal fun ViewModel.serverChoicesOf(profiles: ProfileRepository, states: VpnStateRepository, results: ProbeResults): StateFlow<ServerChoices> =
    profiles.serverChoices(states, results).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ServerChoices())

private fun ProfileRepository.serverChoices(states: VpnStateRepository, results: ProbeResults): Flow<ServerChoices> =
    combine(
        profiles,
        groups,
        states.stats.map { it.unreachableServers }.distinctUntilChanged(),
        results.results
    ) { servers, groups, down, resultMap ->
        ServerChoices(ServerTree(servers, groups.associateBy { it.id }), down, resultMap)
    }

data class ServerLabel(val name: String, val isChain: Boolean, val unreachable: Boolean = false)

sealed interface CurrentServer {
    data object None : CurrentServer
    data object Main : CurrentServer
    data object ByeDpi : CurrentServer
    data class Bound(val serverId: Long) : CurrentServer
}

class ServerChoices(
    val tree: ServerTree = ServerTree(),
    private val unreachable: Set<Long> = emptySet(),
    private val results: Map<Long, ProbeResult> = emptyMap()
) {
    private val byId = servers.associateBy { it.id }
    private val labels = servers.associate { it.id to labelOf(it) }

    val servers: List<ProfileEntity> get() = tree.profiles
    val groups: Map<Long, ServerGroupEntity> get() = tree.groups
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

    fun group(id: Long): ServerNode.Group? = tree.group(id)

    fun resultOf(id: Long): ProbeResult? = results[id]

    fun linksOnly(): ServerChoices = ServerChoices(ServerTree(servers.filter { it.isLink }, groups), unreachable, results)

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
    val holdsCurrent: Boolean

    data class Main(val name: String, val checked: Boolean, val wholeGroup: Boolean = false) : ServerPickerRow {
        override val key: Any get() = "main"
        override val holdsCurrent: Boolean get() = checked
    }

    data class ByeDpi(val checked: Boolean) : ServerPickerRow {
        override val key: Any get() = "byedpi"
        override val holdsCurrent: Boolean get() = checked
    }

    data class Group(val id: Long, val kind: GroupKind, val name: String, val count: Int, val current: String?) : ServerPickerRow {
        override val key: Any get() = "group:$id"
        override val holdsCurrent: Boolean get() = current != null
    }

    data class Server(
        val id: Long,
        val name: String,
        val checked: Boolean,
        val group: String? = null,
        val result: ProbeResult? = null,
        val testing: Boolean = false,
        val protocol: String? = null
    ) : ServerPickerRow {
        override val key: Any get() = id
        override val holdsCurrent: Boolean get() = checked
    }
}

data class ServerPickerPage(
    val folder: ServerFolder,
    val group: String?,
    val rows: List<ServerPickerRow>,
    val testQueue: List<Long>
) {
    val firstShownRow: Int get() = (rows.indexOfFirst { it.holdsCurrent } - ROWS_ABOVE_CURRENT).coerceAtLeast(0)

    private companion object {
        const val ROWS_ABOVE_CURRENT = 1
    }
}

internal fun serverPickerPage(
    choices: ServerChoices,
    query: String,
    folder: ServerFolder,
    current: CurrentServer,
    lead: PickerLead = PickerLead.MAIN_SERVER,
    testing: Set<Long> = emptySet()
): ServerPickerPage {
    val tree = choices.tree
    fun row(server: ProfileEntity, group: String? = null) = ServerPickerRow.Server(
        id = server.id,
        name = server.name,
        checked = current == CurrentServer.Bound(server.id),
        group = group,
        result = choices.resultOf(server.id),
        testing = server.id in testing,
        protocol = server.type.takeIf { server.isLink && it.isNotEmpty() }
    )
    if (query.isNotBlank()) {
        val found = tree.search(ServerFolder.Root, query)
        return ServerPickerPage(
            folder = folder,
            group = null,
            rows = found.map { row(it.profile, it.group?.displayName) },
            testQueue = tree.testQueue(found.map { it.profile.id })
        )
    }
    (folder as? ServerFolder.Group)?.let { tree.group(it.id) }?.let { group ->
        return ServerPickerPage(
            folder = folder,
            group = group.name,
            rows = group.members.map { row(it) },
            testQueue = tree.testQueue(group.profileIds)
        )
    }
    val leading = when (lead) {
        PickerLead.MAIN_SERVER -> choices.main?.let { main ->
            val whole = choices.tree.wholeGroupOf(main)
            ServerPickerRow.Main(whole?.displayName ?: main.name, checked = current == CurrentServer.Main, wholeGroup = whole != null)
        }
        PickerLead.BYEDPI -> ServerPickerRow.ByeDpi(checked = current == CurrentServer.ByeDpi)
        PickerLead.NONE -> null
    }
    val rows = tree.root.map { node ->
        when (node) {
            is ServerNode.Server -> row(node.profile)
            is ServerNode.Group -> ServerPickerRow.Group(
                id = node.id,
                kind = node.kind,
                name = node.name,
                count = node.members.size,
                current = node.members.firstOrNull { current == CurrentServer.Bound(it.id) }?.name
            )
        }
    }
    return ServerPickerPage(folder, group = null, rows = listOfNotNull(leading) + rows, testQueue = tree.testQueue())
}

private val STOP_TIMEOUT = 5_000.milliseconds
