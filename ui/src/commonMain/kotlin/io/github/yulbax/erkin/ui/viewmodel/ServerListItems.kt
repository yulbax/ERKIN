package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage

sealed interface ServerListItem {
    val key: Any
    val profileIds: List<Long>

    data class Server(val profile: ProfileEntity, val group: ServerGroupEntity? = null) : ServerListItem {
        override val key: Any get() = profile.id
        override val profileIds: List<Long> get() = listOf(profile.id)
    }

    data class Group(
        val id: Long,
        val kind: GroupKind,
        val name: String,
        val usage: SubscriptionUsage?,
        val members: List<ProfileEntity>,
        val current: ProfileEntity
    ) : ServerListItem {
        override val key: Any get() = GROUP_KEY_PREFIX + id
        override val profileIds: List<Long> get() = members.map { it.id }
    }

    private companion object {
        const val GROUP_KEY_PREFIX = "group:"
    }
}

internal fun serverListItems(
    profiles: List<ProfileEntity>,
    selectedId: Long?,
    groups: Map<Long, ServerGroupEntity>
): List<ServerListItem> {
    val membersOf = profiles.filter { it.groupId != null }.groupBy { it.groupId }
    val seen = HashSet<Long>()
    return profiles.mapNotNull { profile ->
        val id = profile.groupId
        val members = membersOf[id]
        val stored = id?.let(groups::get)
        when {
            id == null || members == null || stored == null -> ServerListItem.Server(profile)
            members.size == 1 && stored.kind is GroupKind.Subscription -> ServerListItem.Server(profile, stored)
            !seen.add(id) -> null
            else -> {
                ServerListItem.Group(
                    id = id,
                    kind = stored.kind,
                    name = stored.displayName,
                    usage = stored.usage,
                    members = members,
                    current = members.firstOrNull { it.id == selectedId }
                        ?: members.firstOrNull { it.id == stored.currentId }
                        ?: members.first()
                )
            }
        }
    }
}
