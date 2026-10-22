package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage

sealed interface ServerListItem {
    val key: Any

    data class Server(val profile: ProfileEntity, val subscriptionId: Long? = null) : ServerListItem {
        override val key: Any get() = profile.id
    }

    data class Subscription(
        val id: Long,
        val url: String,
        val name: String,
        val usage: SubscriptionUsage?,
        val members: List<ProfileEntity>,
        val current: ProfileEntity
    ) : ServerListItem {
        override val key: Any get() = SUBSCRIPTION_KEY_PREFIX + id
    }

    private companion object {
        const val SUBSCRIPTION_KEY_PREFIX = "subscription:"
    }
}

internal fun serverListItems(
    profiles: List<ProfileEntity>,
    selectedId: Long?,
    subscriptions: Map<Long, SubscriptionEntity>
): List<ServerListItem> {
    val groups = profiles.filter { it.subscriptionId != null }.groupBy { it.subscriptionId }
    val seen = HashSet<Long>()
    return profiles.mapNotNull { profile ->
        val id = profile.subscriptionId
        val members = groups[id]
        val stored = id?.let(subscriptions::get)
        when {
            id == null || members == null || stored == null -> ServerListItem.Server(profile)
            members.size == 1 -> ServerListItem.Server(profile, subscriptionId = id)
            !seen.add(id) -> null
            else -> {
                ServerListItem.Subscription(
                    id = id,
                    url = stored.url,
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
