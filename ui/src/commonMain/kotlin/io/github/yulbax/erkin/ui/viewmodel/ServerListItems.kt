package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.profile.SubscriptionUsage

sealed interface ServerListItem {
    val key: Any

    data class Server(val profile: ProfileEntity, val subscriptionUrl: String? = null) : ServerListItem {
        override val key: Any get() = profile.id
    }

    data class Subscription(
        val url: String,
        val name: String,
        val usage: SubscriptionUsage?,
        val members: List<ProfileEntity>,
        val current: ProfileEntity
    ) : ServerListItem {
        override val key: Any get() = SUBSCRIPTION_KEY_PREFIX + url
    }

    private companion object {
        const val SUBSCRIPTION_KEY_PREFIX = "subscription:"
    }
}

internal fun serverListItems(
    profiles: List<ProfileEntity>,
    selectedId: Long?,
    subscriptions: Map<String, SubscriptionEntity>
): List<ServerListItem> {
    val groups = profiles.filter { it.subscriptionUrl.isNotEmpty() }.groupBy { it.subscriptionUrl }
    val seen = HashSet<String>()
    return profiles.mapNotNull { profile ->
        val url = profile.subscriptionUrl
        val members = groups[url]
        when {
            members == null -> ServerListItem.Server(profile)
            members.size == 1 -> ServerListItem.Server(profile, subscriptionUrl = url)
            !seen.add(url) -> null
            else -> {
                val stored = subscriptions[url]
                ServerListItem.Subscription(
                    url = url,
                    name = stored?.displayName ?: SubscriptionEntity.hostOf(url),
                    usage = stored?.usage,
                    members = members,
                    current = members.firstOrNull { it.id == selectedId }
                        ?: members.firstOrNull { it.id == stored?.currentId }
                        ?: members.first()
                )
            }
        }
    }
}
