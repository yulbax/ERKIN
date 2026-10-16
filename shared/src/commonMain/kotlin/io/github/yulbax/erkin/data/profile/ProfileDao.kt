package io.github.yulbax.erkin.data.profile

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY position, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY position, id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE selected = 1 ORDER BY id LIMIT 1")
    fun observeSelected(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE selected = 1 ORDER BY id LIMIT 1")
    suspend fun getSelected(): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Insert
    suspend fun insertAll(profiles: List<ProfileEntity>): List<Long>

    @Query("SELECT COALESCE(MAX(position), 0) FROM profiles")
    suspend fun lastPosition(): Int

    @Transaction
    suspend fun append(profile: ProfileEntity): Long = insert(profile.copy(position = lastPosition() + 1))

    @Transaction
    suspend fun appendAll(profiles: List<ProfileEntity>): List<Long> {
        val last = lastPosition()
        return insertAll(profiles.mapIndexed { index, profile -> profile.copy(position = last + 1 + index) })
    }

    @Query("UPDATE profiles SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> updatePosition(id, index + 1) }
    }

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE profiles SET name = :name, type = :type, link = :link, outboundJson = :outboundJson WHERE id = :id")
    suspend fun updateConfig(id: Long, name: String, type: String, link: String, outboundJson: String)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Delete
    suspend fun deleteMany(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()

    @Query("UPDATE profiles SET selected = 0")
    suspend fun clearSelection()

    @Query("UPDATE profiles SET selected = 1 WHERE id = :id")
    suspend fun markSelected(id: Long)

    @Transaction
    suspend fun selectExclusive(id: Long) {
        clearSelection()
        markSelected(id)
    }
}
