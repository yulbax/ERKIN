package io.github.yulbax.erkin.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.proxy.ProxyProtocol
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppDatabaseUpgradeTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "erkin-db-${System.nanoTime()}").apply { mkdirs() }
    private val dbFile = File(dir, AppDatabase.FILE_NAME)

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun migratesVersionOneDatabaseAndKeepsData() = runBlocking {
        seed(version = 1, settingsColumns = SETTINGS_V1_COLUMNS, settingsValues = SETTINGS_V1_VALUES, identityHash = "legacy")

        open().useDatabase { database ->
            val settings = requireNotNull(database.settingsDao().getSettings())
            assertEquals(1500, settings.mtu)
            assertEquals("", settings.preferredFingerprint)
            assertFalse(settings.homeHintSeen)
            assertEquals(ConnectionType.DIRECT, settings.newAppsConnectionType(ConnectionType.DIRECT))
            assertSeededRows(database)
        }
        open().useDatabase { database -> assertSeededRows(database) }
    }

    @Test
    fun opensCurrentVersionDatabaseCreatedByPreviousBuild() = runBlocking {
        seed(version = 3, settingsColumns = SETTINGS_V3_COLUMNS, settingsValues = SETTINGS_V3_VALUES, identityHash = V3_IDENTITY_HASH)

        open().useDatabase { database ->
            val settings = requireNotNull(database.settingsDao().getSettings())
            assertEquals("chrome", settings.preferredFingerprint)
            assertEquals(ConnectionType.DIRECT, settings.newAppsConnectionType(ConnectionType.DIRECT))
            assertSeededRows(database)
        }
    }

    @Test
    fun routeAllTrafficBecomesVpnForNewApps() = runBlocking {
        seed(version = 4, settingsColumns = SETTINGS_V4_COLUMNS, settingsValues = SETTINGS_V4_VALUES, identityHash = "legacy")

        open().useDatabase { database ->
            val settings = requireNotNull(database.settingsDao().getSettings())
            assertEquals(ConnectionType.VPN, settings.newAppsConnectionType(ConnectionType.DIRECT))
            assertSeededRows(database)
        }
    }

    @Test
    fun subscriptionServersAreLinkedToOneSubscriptionRecord() = runBlocking {
        seed(version = 4, settingsColumns = SETTINGS_V4_COLUMNS, settingsValues = SETTINGS_V4_VALUES, identityHash = "legacy")
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL(
                "INSERT INTO `profiles` (name, type, link, outboundJson, selected, subscriptionUrl) VALUES " +
                    "('A', 'vless', 'vless://id@a.example:443', '{}', 0, '$SUBSCRIPTION_URL'), " +
                    "('B', 'vless', 'vless://id@b.example:443', '{}', 0, '$SUBSCRIPTION_URL')"
            )
        }

        open().useDatabase { database ->
            val subscription = requireNotNull(database.subscriptionDao().find(SUBSCRIPTION_URL))
            val profiles = database.profileDao().getAll()
            assertEquals(listOf(null, subscription.id, subscription.id), profiles.map { it.subscriptionId })

            database.subscriptionDao().deleteEmpty()
            assertEquals(subscription, database.subscriptionDao().get(subscription.id))
            database.profileDao().deleteMany(profiles.filter { it.subscriptionId != null })
            database.subscriptionDao().deleteEmpty()
            assertNull(database.subscriptionDao().get(subscription.id))
        }
    }

    private suspend fun assertSeededRows(database: AppDatabase) {
        assertEquals(ConnectionType.BYEDPI, database.appDao().getApp("com.example.app")?.connectionType)
        assertNull(database.appDao().getApp("com.example.app")?.path)
        database.appDao().updatePath("com.example.app", "/opt/example/app")
        assertEquals("/opt/example/app", database.appDao().getApp("com.example.app")?.path)
        database.appDao().updatePath("com.example.app", null)
        val profile: ProfileEntity = database.profileDao().observeSelected().first()!!
        assertEquals(ProxyProtocol.VLESS.wire, profile.type)
        assertEquals(ProxyProtocol.VLESS, profile.protocol)
        assertEquals("Example", profile.name)
        assertEquals(profile.id.toInt(), profile.position)
    }

    private fun open(): AppDatabase = AppDatabase.build(Room.databaseBuilder<AppDatabase>(name = dbFile.absolutePath))

    private suspend fun AppDatabase.useDatabase(block: suspend (AppDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun seed(version: Int, settingsColumns: String, settingsValues: String, identityHash: String) {
        BundledSQLiteDriver().open(dbFile.absolutePath).use { connection ->
            connection.execSQL(
                "CREATE TABLE `apps` (`packageName` TEXT NOT NULL, `name` TEXT NOT NULL, `isSystemApp` INTEGER NOT NULL, " +
                    "`connectionType` TEXT NOT NULL, PRIMARY KEY(`packageName`))"
            )
            connection.execSQL("CREATE TABLE `settings` ($settingsColumns, PRIMARY KEY(`id`))")
            connection.execSQL(
                "CREATE TABLE `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, `link` TEXT NOT NULL, `outboundJson` TEXT NOT NULL, `selected` INTEGER NOT NULL, " +
                    "`subscriptionUrl` TEXT NOT NULL)"
            )
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identityHash')")
            connection.execSQL("INSERT INTO `apps` VALUES ('com.example.app', 'Example', 0, 'BYEDPI')")
            connection.execSQL("INSERT INTO `settings` VALUES ($settingsValues)")
            connection.execSQL(
                "INSERT INTO `profiles` (name, type, link, outboundJson, selected, subscriptionUrl) VALUES " +
                    "('Example', 'vless', 'vless://id@example.com:443', '{\"type\":\"vless\"}', 1, '')"
            )
            connection.execSQL("PRAGMA user_version = $version")
        }
    }

    private companion object {
        const val SUBSCRIPTION_URL = "https://sub.example/list"
        const val V3_IDENTITY_HASH = "b93f15925f5dda6d2dc3488b9b0a0006"
        const val SETTINGS_V1_COLUMNS =
            "`id` INTEGER NOT NULL, `showSystemApps` INTEGER NOT NULL, `byeDpiArgs` TEXT NOT NULL, `tunStack` TEXT NOT NULL, " +
                "`mtu` INTEGER NOT NULL, `ipv6Mode` TEXT NOT NULL, `dnsRemote` TEXT NOT NULL, `dnsDirect` TEXT NOT NULL, " +
                "`sniff` INTEGER NOT NULL, `bypassLan` INTEGER NOT NULL, `autoConnect` INTEGER NOT NULL"
        const val SETTINGS_V3_COLUMNS = SETTINGS_V1_COLUMNS +
            ", `preferredFingerprint` TEXT NOT NULL, `homeHintSeen` INTEGER NOT NULL, `appsHintSeen` INTEGER NOT NULL"
        const val SETTINGS_V1_VALUES = "1, 0, '', 'gvisor', 1500, 'disable', '1.1.1.1', '1.1.1.1', 1, 0, 0"
        const val SETTINGS_V3_VALUES = "$SETTINGS_V1_VALUES, 'chrome', 1, 0"
        const val SETTINGS_V4_COLUMNS = "$SETTINGS_V3_COLUMNS, `routeAllTraffic` INTEGER NOT NULL"
        const val SETTINGS_V4_VALUES = "$SETTINGS_V3_VALUES, 1"
    }
}
