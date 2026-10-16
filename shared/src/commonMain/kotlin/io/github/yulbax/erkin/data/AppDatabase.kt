package io.github.yulbax.erkin.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.yulbax.erkin.data.profile.ProfileDao
import io.github.yulbax.erkin.data.profile.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [App::class, SettingsEntity::class, ProfileEntity::class],
    version = 7,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao

    suspend fun <R> transaction(block: suspend () -> R): R =
        useWriterConnection { connection -> connection.immediateTransaction { block() } }

    companion object {
        const val FILE_NAME = "erkin.db"

        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN preferredFingerprint TEXT NOT NULL DEFAULT ''")
                }
            },
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN homeHintSeen INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("ALTER TABLE settings ADD COLUMN appsHintSeen INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN routeAllTraffic INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(4, 5) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE apps ADD COLUMN path TEXT")
                }
            },
            object : Migration(5, 6) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN newAppsConnectionType TEXT NOT NULL DEFAULT ''")
                    connection.execSQL("UPDATE settings SET newAppsConnectionType = 'VPN' WHERE routeAllTraffic = 1")
                    connection.execSQL("ALTER TABLE settings DROP COLUMN routeAllTraffic")
                }
            },
            object : Migration(6, 7) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE profiles ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("UPDATE profiles SET position = id")
                }
            }
        )

        fun build(builder: Builder<AppDatabase>): AppDatabase =
            builder
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
