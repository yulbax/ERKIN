package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage
import io.github.yulbax.erkin.data.profile.shownServer

sealed interface ServerListItem {
    val key: Any
    val profileIds: List<Long>
    val server: ProfileEntity

    data class Server(val profile: ProfileEntity, val group: ServerGroupEntity? = null) : ServerListItem {
        override val key: Any get() = profile.id
        override val profileIds: List<Long> get() = listOf(profile.id)
        override val server: ProfileEntity get() = profile
    }

    data class Group(
        val id: Long,
        val kind: GroupKind,
        val name: String,
        val usage: SubscriptionUsage?,
        val members: List<ProfileEntity>,
        val rememberedId: Long?,
        val updatedAt: Long? = null
    ) : ServerListItem {
        val current: ProfileEntity = members.shownServer(rememberedId)

        override val key: Any get() = GROUP_KEY_PREFIX + id
        override val profileIds: List<Long> get() = members.map { it.id }
        override val server: ProfileEntity get() = current
    }

    private companion object {
        const val GROUP_KEY_PREFIX = "group:"
    }
}

internal fun serverListItems(profiles: List<ProfileEntity>, groups: Map<Long, ServerGroupEntity>): List<ServerListItem> {
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
                    rememberedId = stored.currentId,
                    updatedAt = stored.updatedAt.takeIf { stored.kind is GroupKind.Subscription && it > 0 }
                )
            }
        }
    }
}
