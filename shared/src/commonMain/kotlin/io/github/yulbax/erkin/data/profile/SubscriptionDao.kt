package io.github.yulbax.erkin.data.profile

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE url = :url")
    suspend fun get(url: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE url NOT IN (SELECT subscriptionUrl FROM profiles)")
    suspend fun deleteOrphans()
}
