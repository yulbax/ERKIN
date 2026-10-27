package io.github.yulbax.erkin.data.byedpi

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "byedpi_args", indices = [Index(value = ["args"], unique = true)])
data class ByeDpiArgsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val args: String,
    val name: String = "",
    val pinned: Boolean = false,
    val lastUsedAt: Long = 0
)

@Entity(
    tableName = "byedpi_site_results",
    primaryKeys = ["argsId", "network", "host"],
    foreignKeys = [
        ForeignKey(
            entity = ByeDpiArgsEntity::class,
            parentColumns = ["id"],
            childColumns = ["argsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("argsId")]
)
data class ByeDpiSiteResultEntity(
    val argsId: Long,
    val network: String,
    val host: String,
    val siteGroup: String,
    val reachable: Boolean,
    val testedAt: Long
)

@Dao
interface ByeDpiHistoryDao {

    @Query("SELECT * FROM byedpi_args ORDER BY pinned DESC, id DESC")
    fun observeArgs(): Flow<List<ByeDpiArgsEntity>>

    @Query("SELECT * FROM byedpi_site_results")
    fun observeResults(): Flow<List<ByeDpiSiteResultEntity>>

    @Query("SELECT * FROM byedpi_args WHERE args = :args")
    suspend fun find(args: String): ByeDpiArgsEntity?

    @Insert
    suspend fun insert(entry: ByeDpiArgsEntity): Long

    @Query("UPDATE byedpi_args SET lastUsedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("UPDATE byedpi_args SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE byedpi_args SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM byedpi_args WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "DELETE FROM byedpi_args WHERE pinned = 0 AND args != :active AND id NOT IN " +
            "(SELECT id FROM byedpi_args WHERE pinned = 0 ORDER BY lastUsedAt DESC LIMIT :keep)"
    )
    suspend fun trimUnpinned(keep: Int, active: String)

    @Query("DELETE FROM byedpi_site_results WHERE argsId = :argsId AND network = :network")
    suspend fun clearResults(argsId: Long, network: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<ByeDpiSiteResultEntity>)
}
