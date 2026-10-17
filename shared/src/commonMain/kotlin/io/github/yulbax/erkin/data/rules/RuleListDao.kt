package io.github.yulbax.erkin.data.rules

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleListDao {

    @Query("SELECT * FROM rule_lists")
    fun observeAll(): Flow<List<RuleListEntity>>

    @Query("SELECT * FROM rule_lists WHERE source = :source")
    suspend fun get(source: String): RuleListEntity?

    @Upsert
    suspend fun upsert(list: RuleListEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(list: RuleListEntity)

    @Query("UPDATE rule_lists SET name = :name WHERE source = :source")
    suspend fun rename(source: String, name: String?)

    @Query("DELETE FROM rule_lists WHERE source = :source")
    suspend fun delete(source: String)
}
