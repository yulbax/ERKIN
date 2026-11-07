package io.github.yulbax.erkin.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BypassChannelTest {

    @Test
    fun anAndroidUpgradeMovesDirectAppsOutOfErkinUnlessTheyHaveRules() = upgrade(appsCanBypass = true, tunnelDirectApps = false) { connection ->
        assertEquals(mapOf("bank" to "BYPASS", "ruled" to "DIRECT", "vpn" to "VPN"), connection.channels("apps"))
        assertEquals(mapOf("pending" to "BYPASS"), connection.channels("pending_apps"))
        assertEquals("BYPASS", connection.text("SELECT newAppsConnectionType FROM settings"))
    }

    @Test
    fun appsThatWentThroughErkinBeforeStayDirect() = upgrade(appsCanBypass = true, tunnelDirectApps = true) { connection ->
        assertEquals(mapOf("bank" to "DIRECT", "ruled" to "DIRECT", "vpn" to "VPN"), connection.channels("apps"))
        assertEquals("DIRECT", connection.text("SELECT newAppsConnectionType FROM settings"))
    }

    @Test
    fun theDesktopKeepsDirectApps() = upgrade(appsCanBypass = false, tunnelDirectApps = false) { connection ->
        assertEquals(mapOf("bank" to "DIRECT", "ruled" to "DIRECT", "vpn" to "VPN"), connection.channels("apps"))
    }

    @Test
    fun anOldBackupRestoresDirectAppsAsBypassOnAndroid() {
        val backup = AppConfigBackup(
            version = 8,
            apps = mapOf("bank" to "DIRECT", "ruled" to "DIRECT", "vpn" to "VPN"),
            settings = SettingsEntity(newAppsConnectionType = "DIRECT"),
            rules = listOf(RoutingRuleEntity(type = RuleMatch.DOMAIN.wire, value = "a.example", target = RuleTarget.VPN.wire, app = "ruled"))
        )

        val android = backup.adaptedTo(appsCanBypass = true)
        assertEquals(mapOf("bank" to "BYPASS", "ruled" to "DIRECT", "vpn" to "VPN"), android.apps)
        assertEquals("BYPASS", android.settings?.newAppsConnectionType)
        assertEquals(backup.apps, backup.copy(version = AppConfigBackup.CURRENT_VERSION).adaptedTo(appsCanBypass = true).apps)
    }

    @Test
    fun anOldBackupThatRoutedAllTrafficThroughErkinKeepsDirectApps() {
        val text = """{"version":8,"apps":{"bank":"DIRECT"},"settings":{"newAppsConnectionType":"DIRECT","tunnelDirectApps":true}}"""
        val backup = requireNotNull(BackupCodec.decode(text)).adaptedTo(appsCanBypass = true)

        assertEquals(mapOf("bank" to "DIRECT"), backup.apps)
        assertEquals("DIRECT", backup.settings?.newAppsConnectionType)
    }

    @Test
    fun theDesktopRestoresBypassAppsAsDirect() {
        val backup = AppConfigBackup(version = AppConfigBackup.CURRENT_VERSION, apps = mapOf("bank" to "BYPASS"))

        assertEquals(mapOf("bank" to "DIRECT"), backup.adaptedTo(appsCanBypass = false).apps)
    }

    private fun upgrade(appsCanBypass: Boolean, tunnelDirectApps: Boolean, check: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE apps (packageName TEXT NOT NULL PRIMARY KEY, connectionType TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE pending_apps (packageName TEXT NOT NULL PRIMARY KEY, connectionType TEXT NOT NULL)")
            connection.execSQL("CREATE TABLE settings (id INTEGER NOT NULL PRIMARY KEY, newAppsConnectionType TEXT NOT NULL, tunnelDirectApps INTEGER NOT NULL)")
            connection.execSQL("CREATE TABLE routing_rules (id INTEGER PRIMARY KEY, app TEXT)")
            connection.execSQL("INSERT INTO apps VALUES ('bank', 'DIRECT'), ('ruled', 'DIRECT'), ('vpn', 'VPN')")
            connection.execSQL("INSERT INTO pending_apps VALUES ('pending', 'DIRECT')")
            connection.execSQL("INSERT INTO settings VALUES (1, 'DIRECT', ${if (tunnelDirectApps) 1 else 0})")
            connection.execSQL("INSERT INTO routing_rules VALUES (1, 'ruled'), (2, NULL)")

            AppDatabase.migrations(appsCanBypass).single { it.startVersion == 25 }.migrate(connection)

            check(connection)
            assertFalse("tunnelDirectApps" in connection.columns("settings"))
        } finally {
            connection.close()
        }
    }

    private fun SQLiteConnection.channels(table: String): Map<String, String> = buildMap {
        prepare("SELECT packageName, connectionType FROM $table").use { while (it.step()) put(it.getText(0), it.getText(1)) }
    }

    private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { it.step(); it.getText(0) }

    private fun SQLiteConnection.columns(table: String): List<String> = buildList {
        prepare("PRAGMA table_info($table)").use { while (it.step()) add(it.getText(1)) }
    }
}
