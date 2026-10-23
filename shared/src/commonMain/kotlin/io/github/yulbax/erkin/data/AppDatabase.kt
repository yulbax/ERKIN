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
import io.github.yulbax.erkin.data.profile.SubscriptionDao
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.data.rules.RoutingRuleDao
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RuleListDao
import io.github.yulbax.erkin.data.rules.RuleListEntity
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [App::class, SettingsEntity::class, ProfileEntity::class, RoutingRuleEntity::class, RuleListEntity::class, SubscriptionEntity::class, PendingApp::class],
    version = 19,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun routingRuleDao(): RoutingRuleDao
    abstract fun ruleListDao(): RuleListDao
    abstract fun subscriptionDao(): SubscriptionDao

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
            },
            object : Migration(7, 8) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `routing_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`type` TEXT NOT NULL, `value` TEXT NOT NULL, `target` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                    )
                }
            },
            object : Migration(8, 9) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN rulesHintSeen INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(9, 10) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN blockQuic INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(10, 11) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN chainsHintSeen INTEGER NOT NULL DEFAULT 0")
                }
            },
            object : Migration(11, 12) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `rule_lists` (`source` TEXT NOT NULL, `title` TEXT, " +
                            "`entries` INTEGER NOT NULL, `failure` TEXT, `failureDetail` TEXT, PRIMARY KEY(`source`))"
                    )
                }
            },
            object : Migration(12, 13) {
                override fun migrate(connection: SQLiteConnection) {
                    val ruleSet = RuleMatch.RULE_SET.wire
                    val countryUrl = "'${RuleLists.COUNTRY_URL_PREFIX}' || lower(value) || '${RuleLists.COUNTRY_URL_SUFFIX}'"
                    val country = RoutingRuleEntity.LEGACY_COUNTRY
                    val list = RoutingRuleEntity.LEGACY_LIST
                    connection.execSQL("ALTER TABLE rule_lists ADD COLUMN name TEXT")
                    connection.execSQL("ALTER TABLE rule_lists ADD COLUMN kind TEXT")
                    connection.execSQL(
                        "INSERT OR IGNORE INTO rule_lists (source, entries) " +
                            "SELECT $countryUrl, 0 FROM routing_rules WHERE type = '$country'"
                    )
                    connection.execSQL("INSERT OR IGNORE INTO rule_lists (source, entries) SELECT value, 0 FROM routing_rules WHERE type = '$list'")
                    connection.execSQL(
                        "UPDATE rule_lists SET kind = '${RuleSetKind.IPCIDR.behavior}' " +
                            "WHERE source LIKE '${RuleLists.COUNTRY_URL_PREFIX}%'"
                    )
                    connection.execSQL("UPDATE routing_rules SET type = '$ruleSet', value = $countryUrl WHERE type = '$country'")
                    connection.execSQL("UPDATE routing_rules SET type = '$ruleSet' WHERE type = '$list'")
                }
            },
            object : Migration(13, 14) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `subscriptions` (`url` TEXT NOT NULL, `name` TEXT, `title` TEXT, " +
                            "`upload` INTEGER, `download` INTEGER, `total` INTEGER, `expire` INTEGER, " +
                            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
                    )
                }
            },
            object : Migration(14, 15) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE subscriptions ADD COLUMN currentId INTEGER")
                }
            },
            object : Migration(15, 16) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE `subscriptions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                            "`name` TEXT, `title` TEXT, `upload` INTEGER, `download` INTEGER, `total` INTEGER, `expire` INTEGER, " +
                            "`updatedAt` INTEGER NOT NULL, `currentId` INTEGER)"
                    )
                    connection.execSQL(
                        "INSERT INTO subscriptions_new (url, name, title, upload, download, total, expire, updatedAt, currentId) " +
                            "SELECT url, name, title, upload, download, total, expire, updatedAt, currentId FROM subscriptions"
                    )
                    connection.execSQL(
                        "INSERT INTO subscriptions_new (url, updatedAt) SELECT DISTINCT subscriptionUrl, 0 FROM profiles " +
                            "WHERE subscriptionUrl != '' AND subscriptionUrl NOT IN (SELECT url FROM subscriptions_new)"
                    )
                    connection.execSQL("DROP TABLE subscriptions")
                    connection.execSQL("ALTER TABLE subscriptions_new RENAME TO subscriptions")
                    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subscriptions_url` ON `subscriptions` (`url`)")
                    connection.execSQL(
                        "CREATE TABLE `profiles_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, `link` TEXT NOT NULL, `outboundJson` TEXT NOT NULL, `selected` INTEGER NOT NULL, " +
                            "`subscriptionId` INTEGER, `position` INTEGER NOT NULL, FOREIGN KEY(`subscriptionId`) " +
                            "REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    connection.execSQL(
                        "INSERT INTO profiles_new (id, name, type, link, outboundJson, selected, subscriptionId, position) " +
                            "SELECT p.id, p.name, p.type, p.link, p.outboundJson, p.selected, s.id, p.position FROM profiles p " +
                            "LEFT JOIN subscriptions s ON s.url = p.subscriptionUrl"
                    )
                    connection.execSQL("DROP TABLE profiles")
                    connection.execSQL("ALTER TABLE profiles_new RENAME TO profiles")
                    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_subscriptionId` ON `profiles` (`subscriptionId`)")
                }
            },
            object : Migration(16, 17) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE settings ADD COLUMN sortServersByPing INTEGER NOT NULL DEFAULT 1")
                }
            },
            object : Migration(17, 18) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE apps ADD COLUMN serverId INTEGER DEFAULT NULL")
                }
            },
            object : Migration(18, 19) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "CREATE TABLE IF NOT EXISTS `pending_apps` (`packageName` TEXT NOT NULL, " +
                            "`connectionType` TEXT NOT NULL, `serverId` INTEGER, PRIMARY KEY(`packageName`))"
                    )
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
