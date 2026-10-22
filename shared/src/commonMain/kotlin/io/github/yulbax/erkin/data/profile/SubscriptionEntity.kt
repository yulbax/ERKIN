package io.github.yulbax.erkin.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.proxy.ParsedProfile
import java.net.URI

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val url: String,
    val name: String? = null,
    val title: String? = null,
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expire: Long? = null,
    val updatedAt: Long = 0,
    val currentId: Long? = null
) {
    val usage: SubscriptionUsage?
        get() = if (upload == null || download == null || total == null || expire == null) null
        else SubscriptionUsage(upload, download, total, expire)

    val displayName: String get() = name ?: title ?: hostOf(url)

    companion object {
        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
    }
}

data class SubscriptionUsage(val upload: Long, val download: Long, val total: Long, val expire: Long) {
    val used: Long get() = upload + download
}

data class SubscriptionInfo(val title: String? = null, val usage: SubscriptionUsage? = null)

data class FetchedSubscription(
    val profiles: List<ParsedProfile>,
    val info: SubscriptionInfo = SubscriptionInfo()
)
