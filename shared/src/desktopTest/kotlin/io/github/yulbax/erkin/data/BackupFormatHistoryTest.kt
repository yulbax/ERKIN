package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatHistoryTest {

    @Test
    fun appsOnlyBackupsFromVersionOneStillImport() {
        val backup = read(1)
        assertEquals(mapOf("org.telegram.messenger" to "VPN", "com.discord" to "BYEDPI"), backup.apps)
    }

    @Test
    fun settingsStoredAsDatabaseRowsInVersionTwoStillImport() {
        val settings = requireNotNull(read(2).settings)
        assertEquals(1500, settings.mtu)
        assertEquals("8.8.8.8", settings.dnsRemote)
        assertTrue(settings.showSystemApps)
        assertEquals(Ipv6Mode.ENABLE, settings.networkOptions().ipv6Mode)
        assertEquals(SettingsEntity().tunStack, settings.tunStack)
    }

    @Test
    fun profilesStoredAsDatabaseRowsInVersionThreeStillImport() {
        val backup = read(3)
        assertEquals(1400, backup.settings?.mtu)
        assertEquals("firefox", backup.settings?.preferredFingerprint)
        val profile = requireNotNull(backup.profiles).single()
        assertEquals("Example", profile.name)
        assertTrue(profile.selected)
    }

    @Test
    fun versionFourBackupsStillImport() {
        val backup = read(4)
        assertEquals(1380, backup.settings?.mtu)
        assertEquals(ConnectionType.VPN, backup.settings?.newAppsConnectionType(ConnectionType.DIRECT))
        assertEquals("Example", requireNotNull(backup.profiles).single().name)
    }

    @Test
    fun versionFiveBackupsStillImport() {
        val backup = read(5)
        assertEquals(1360, backup.settings?.mtu)
        assertTrue(backup.settings?.blockQuic == true)
        assertEquals("ru", requireNotNull(backup.rules).single().value)
    }

    @Test
    fun versionSixBackupsCarryChains() {
        val backup = read(6)
        val chain = requireNotNull(backup.profiles).single { it.type == "chain" }
        assertTrue(chain.selected)
        val rule = requireNotNull(backup.rules).single()
        assertEquals(RuleMatch.RULE_SET.wire, rule.type)
        assertEquals(RuleLists.country("RU").url, rule.value)
    }

    @Test
    fun exportedSettingsLeaveOutDatabaseOnlyColumns() {
        val encoded = BackupCodec.encode(AppConfigBackup(version = AppConfigBackup.CURRENT_VERSION, settings = SettingsEntity(mtu = 1500)))
        assertTrue(encoded.contains("\"mtu\""))
        assertTrue(!encoded.contains("\"id\"") && !encoded.contains("\"tunStack\""))
    }

    private fun read(version: Int): AppConfigBackup {
        val text = requireNotNull(javaClass.getResource("/backups/v$version.json")) { "missing v$version fixture" }.readText()
        return requireNotNull(BackupCodec.decode(text)) { "v$version backup no longer imports" }
    }
}
