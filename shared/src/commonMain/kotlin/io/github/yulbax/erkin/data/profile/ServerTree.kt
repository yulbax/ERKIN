package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.vpn.core.ProbeResult

sealed interface ServerFolder {
    data object Root : ServerFolder
    data class Group(val id: Long) : ServerFolder
}

sealed interface ServerNode {
    val key: Any
    val profileIds: List<Long>
    val server: ProfileEntity
    val others: List<ProfileEntity>
    val storedIds: List<Long>

    data class Server(val profile: ProfileEntity, val group: ServerGroupEntity? = null) : ServerNode {
        override val key: Any get() = profile.id
        override val profileIds: List<Long> get() = listOf(profile.id)
        override val server: ProfileEntity get() = profile
        override val others: List<ProfileEntity> get() = emptyList()
        override val storedIds: List<Long> get() = profileIds
    }

    data class Group(
        val id: Long,
        val kind: GroupKind,
        val name: String,
        val usage: SubscriptionUsage?,
        val members: List<ProfileEntity>,
        val rememberedId: Long?,
        val updatedAt: Long? = null,
        val hidden: List<ProfileEntity> = emptyList()
    ) : ServerNode {
        val current: ProfileEntity = members.ifEmpty { hidden }.shownServer(rememberedId)

        override val key: Any get() = GROUP_KEY_PREFIX + id
        override val profileIds: List<Long> get() = members.map { it.id }
        override val server: ProfileEntity get() = current
        override val others: List<ProfileEntity> get() = members.filter { it.id != current.id }
        override val storedIds: List<Long> get() = profileIds + hidden.map { it.id }
    }

    private companion object {
        const val GROUP_KEY_PREFIX = "group:"
    }
}

class ServerTree(
    val profiles: List<ProfileEntity> = emptyList(),
    val groups: Map<Long, ServerGroupEntity> = emptyMap()
) {
    val root: List<ServerNode> = nodesOf(profiles, groups)

    private val testOrder: List<Long> = (root.map { it.server } + root.flatMap { it.others }).map { it.id }

    fun group(id: Long): ServerNode.Group? = root.firstOrNull { it is ServerNode.Group && it.id == id } as ServerNode.Group?

    fun children(folder: ServerFolder): List<ServerNode> = when (folder) {
        ServerFolder.Root -> root
        is ServerFolder.Group -> group(folder.id)?.members.orEmpty().map { ServerNode.Server(it) }
    }

    fun hidden(folder: ServerFolder): List<ProfileEntity> = when (folder) {
        ServerFolder.Root -> emptyList()
        is ServerFolder.Group -> group(folder.id)?.hidden.orEmpty()
    }

    fun search(folder: ServerFolder, query: String): List<ServerNode.Server> = when (folder) {
        ServerFolder.Root -> profiles.offered().matching(query).map { ServerNode.Server(it, it.groupId?.let(groups::get)) }
        is ServerFolder.Group -> group(folder.id)?.members.orEmpty().matching(query).map { ServerNode.Server(it) }
    }

    fun testQueue(servers: Collection<Long>): List<Long> = servers.toSet().let { ids -> testOrder.filter { it in ids } }

    fun testQueue(): List<Long> = testOrder

    fun orderedByName(): List<Long> =
        root.sortedWith(compareBy(ServerNameOrder) { node -> (node as? ServerNode.Group)?.name ?: node.server.name })
            .flatMap { node ->
                when (node) {
                    is ServerNode.Server -> node.storedIds
                    is ServerNode.Group -> (node.members.byName() + node.hidden.byName()).map { it.id }
                }
            }

    fun orderedByDelay(tested: Set<Long>, results: Map<Long, ProbeResult>): List<Long> =
        ServerTree(profiles.sortedByDelay(tested, results) { it }, groups).root
            .sortedByDelay(tested, results) { it.server }
            .flatMap { it.storedIds }

    private fun List<ProfileEntity>.byName(): List<ProfileEntity> = sortedWith(compareBy(ServerNameOrder) { it.name })

    private companion object {
        fun nodesOf(profiles: List<ProfileEntity>, groups: Map<Long, ServerGroupEntity>): List<ServerNode> {
            val membersOf = profiles.filter { it.groupId != null }.groupBy { it.groupId }
            val seen = HashSet<Long>()
            return profiles.mapNotNull { profile ->
                val id = profile.groupId
                val members = membersOf[id]
                val stored = id?.let(groups::get)
                when {
                    id == null || members == null || stored == null -> ServerNode.Server(profile)
                    stored.showsAsServer(members.size) -> ServerNode.Server(profile, stored)
                    !seen.add(id) -> null
                    else -> ServerNode.Group(
                        id = id,
                        kind = stored.kind,
                        name = stored.displayName,
                        usage = stored.usage,
                        members = members.filterNot { it.hidden },
                        rememberedId = stored.currentId,
                        updatedAt = stored.updatedAt.takeIf { stored.kind is GroupKind.Subscription && it > 0 },
                        hidden = members.filter { it.hidden }
                    )
                }
            }
        }

        fun <T> List<T>.sortedByDelay(tested: Set<Long>, results: Map<Long, ProbeResult>, server: (T) -> ProfileEntity): List<T> {
            val isTested: (T) -> Boolean = { server(it).id in tested }
            val ranked = filter(isTested).sortedBy { results[server(it).id].rank() }.iterator()
            return map { if (isTested(it)) ranked.next() else it }
        }

        fun ProbeResult?.rank(): Int = when (this) {
            is ProbeResult.Answered -> ms
            null, ProbeResult.NeedsConnection -> Int.MAX_VALUE - 1
            ProbeResult.NoAnswer, ProbeResult.LoginRejected -> Int.MAX_VALUE
        }
    }
}

fun List<ProfileEntity>.matching(query: String): List<ProfileEntity> {
    val needle = query.trim()
    return if (needle.isEmpty()) this else filter { it.name.contains(needle, ignoreCase = true) }
}

fun List<ProfileEntity>.offered(): List<ProfileEntity> = filterNot { it.hidden }
