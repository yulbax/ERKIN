package io.github.yulbax.erkin.data.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerGroupDao {

    @Query("SELECT * FROM server_groups")
    fun observeAll(): Flow<List<ServerGroupEntity>>

    @Query("SELECT * FROM server_groups")
    suspend fun getAll(): List<ServerGroupEntity>

    @Query("SELECT * FROM server_groups WHERE id = :id")
    suspend fun get(id: Long): ServerGroupEntity?

    @Query("SELECT * FROM server_groups WHERE url = :url")
    suspend fun find(url: String): ServerGroupEntity?

    @Insert
    suspend fun insert(group: ServerGroupEntity): Long

    @Update
    suspend fun update(group: ServerGroupEntity)

    @Transaction
    suspend fun obtain(url: String): ServerGroupEntity =
        find(url) ?: ServerGroupEntity(url = url).let { it.copy(id = insert(it)) }

    @Query("UPDATE server_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE server_groups SET currentId = :profileId WHERE id = :id")
    suspend fun updateCurrent(id: Long, profileId: Long)

    @Query("DELETE FROM server_groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM server_groups WHERE id NOT IN (SELECT groupId FROM profiles WHERE groupId IS NOT NULL)")
    suspend fun deleteEmpty()
}
