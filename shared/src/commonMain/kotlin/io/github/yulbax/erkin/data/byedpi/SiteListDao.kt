package io.github.yulbax.erkin.data.byedpi

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "byedpi_site_lists", indices = [Index(value = ["preset"], unique = true)])
data class SiteListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preset: String? = null,
    val name: String? = null,
    val sites: String? = null,
    val active: Boolean = false
)

@Dao
interface SiteListDao {

    @Query("SELECT * FROM byedpi_site_lists ORDER BY id")
    fun observe(): Flow<List<SiteListEntity>>

    @Query("SELECT * FROM byedpi_site_lists ORDER BY id")
    suspend fun getAll(): List<SiteListEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rows: List<SiteListEntity>)

    @Insert
    suspend fun insert(row: SiteListEntity): Long

    @Query("UPDATE byedpi_site_lists SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE byedpi_site_lists SET name = :name, sites = :sites WHERE id = :id")
    suspend fun edit(id: Long, name: String?, sites: String?)

    @Query("UPDATE byedpi_site_lists SET name = :name, sites = :sites, active = :active WHERE id = :id")
    suspend fun replace(id: Long, name: String?, sites: String?, active: Boolean)

    @Query("DELETE FROM byedpi_site_lists WHERE id = :id AND preset IS NULL")
    suspend fun deleteCustom(id: Long)
}
