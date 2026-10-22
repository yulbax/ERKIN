package io.github.yulbax.erkin.data.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE url = :url")
    suspend fun find(url: String): SubscriptionEntity?

    @Insert
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Transaction
    suspend fun obtain(url: String): SubscriptionEntity =
        find(url) ?: SubscriptionEntity(url = url).let { it.copy(id = insert(it)) }

    @Query("UPDATE subscriptions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE subscriptions SET currentId = :profileId WHERE id = :id")
    suspend fun updateCurrent(id: Long, profileId: Long)

    @Query("DELETE FROM subscriptions WHERE id NOT IN (SELECT subscriptionId FROM profiles WHERE subscriptionId IS NOT NULL)")
    suspend fun deleteEmpty()
}
