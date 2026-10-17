package io.github.yulbax.erkin.data.rules

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingRuleDao {

    @Query("SELECT * FROM routing_rules ORDER BY position, id")
    fun observeAll(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules ORDER BY position, id")
    suspend fun getAll(): List<RoutingRuleEntity>

    @Insert
    suspend fun insert(rule: RoutingRuleEntity): Long

    @Update
    suspend fun update(rule: RoutingRuleEntity)

    @Delete
    suspend fun delete(rule: RoutingRuleEntity)

    @Delete
    suspend fun deleteMany(rules: List<RoutingRuleEntity>)

    @Query("DELETE FROM routing_rules WHERE type = :type AND value = :value")
    suspend fun deleteMatching(type: String, value: String)

    @Query("SELECT COALESCE(MAX(position), 0) FROM routing_rules")
    suspend fun lastPosition(): Int

    @Transaction
    suspend fun append(rule: RoutingRuleEntity): Long = insert(rule.copy(position = lastPosition() + 1))

    @Query("UPDATE routing_rules SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> updatePosition(id, index + 1) }
    }
}
