package io.github.yulbax.erkin.data.profile

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.proxy.ParsedProfile
import java.net.URI

@Entity(tableName = "server_groups", indices = [Index("url", unique = true)])
data class ServerGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String? = null,
    val name: String? = null,
    val title: String? = null,
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expire: Long? = null,
    val updatedAt: Long = 0,
    val currentId: Long? = null
) {
    val kind: GroupKind get() = url?.let(GroupKind::Subscription) ?: GroupKind.Local

    val usage: SubscriptionUsage?
        get() = if (upload == null || download == null || total == null || expire == null) null
        else SubscriptionUsage(upload, download, total, expire)

    val displayName: String get() = name ?: title ?: url?.let(::hostOf).orEmpty()

    companion object {
        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
    }
}

fun List<ProfileEntity>.shownServer(rememberedId: Long?): ProfileEntity =
    find { it.id == rememberedId } ?: first()

sealed interface GroupKind {
    val acceptsServers: Boolean

    data class Subscription(val url: String) : GroupKind {
        override val acceptsServers: Boolean get() = false
    }

    data object Local : GroupKind {
        override val acceptsServers: Boolean get() = true
    }
}

sealed interface AddTarget {
    data class Main(val newGroupName: String) : AddTarget
    data class Group(val id: Long) : AddTarget
}

sealed interface GroupTarget {
    data class New(val name: String) : GroupTarget
    data class Existing(val id: Long) : GroupTarget
}

data class SubscriptionUsage(val upload: Long, val download: Long, val total: Long, val expire: Long) {
    val used: Long get() = upload + download
}

data class SubscriptionInfo(val title: String? = null, val usage: SubscriptionUsage? = null)

data class FetchedSubscription(
    val profiles: List<ParsedProfile>,
    val info: SubscriptionInfo = SubscriptionInfo()
)
