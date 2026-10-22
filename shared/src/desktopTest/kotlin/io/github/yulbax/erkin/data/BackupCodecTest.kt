package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.util.readBounded
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun acceptsLegacyV1AppsOnlyBackup() {
        val backup = BackupCodec.decode(
            """{"version":1,"apps":{"com.example.app":"VPN"}}"""
        )

        assertNotNull(backup)
        assertEquals("VPN", backup?.apps?.get("com.example.app"))
    }

    @Test
    fun readsLegacyEntityFieldsAndPreservesSelectedProfile() {
        val outbound = Json.encodeToString(
            """{"type":"vless","server":"example.com","server_port":443}"""
        )
        val legacy = """
            {
              "version": 3,
              "settings": {"id": 1, "mtu": 1500},
              "profiles": [{
                "id": 42,
                "name": "Example",
                "type": "vless",
                "link": "$VALID_LINK",
                "outboundJson": $outbound,
                "selected": true,
                "subscriptionUrl": ""
              }]
            }
        """.trimIndent()

        val backup = BackupCodec.decode(legacy)

        assertNotNull(backup)
        assertEquals(1500, backup?.settings?.mtu)
        assertTrue(requireNotNull(backup?.profiles).single().selected)
    }

    @Test
    fun acceptsBackupsThatStillCarryARemovedTunStack() {
        val backup = BackupCodec.decode(
            """{"version":4,"settings":{"tunStack":"system","mtu":1500}}"""
        )

        assertNotNull(backup)
        assertEquals(1500, backup?.settings?.mtu)
    }

    @Test
    fun removedIpv6ModesStillImportAsEnabled() {
        val backup = requireNotNull(
            BackupCodec.decode("""{"version":4,"settings":{"ipv6Mode":"prefer"}}""")
        )

        val settings = requireNotNull(backup.settings)
        assertEquals(Ipv6Mode.ENABLE, settings.networkOptions().ipv6Mode)
        assertEquals(Ipv6Mode.ENABLE, SettingsEntity(ipv6Mode = "only").networkOptions().ipv6Mode)
        assertFalse(BackupCodec.isValid(backup.copy(settings = SettingsEntity(ipv6Mode = "sometimes"))))
    }

    @Test
    fun rejectsUnsupportedVersionsAndInvalidSettings() {
        assertFalse(BackupCodec.isValid(validBackup().copy(version = 0)))
        assertFalse(
            BackupCodec.isValid(
                validBackup().copy(version = AppConfigBackup.CURRENT_VERSION + 1)
            )
        )
        assertFalse(
            BackupCodec.isValid(
                validBackup().copy(settings = SettingsEntity(mtu = 100))
            )
        )
    }

    @Test
    fun rejectsAmbiguousSelectionAndMismatchedProfileType() {
        val profile = requireNotNull(validBackup().profiles).single()
        assertFalse(
            BackupCodec.isValid(
                validBackup().copy(profiles = listOf(profile, profile.copy(name = "Second")))
            )
        )
        assertFalse(
            BackupCodec.isValid(
                validBackup().copy(profiles = listOf(profile.copy(type = "trojan")))
            )
        )
    }

    @Test
    fun onlyServerIdsLeaveTheDatabaseAsBackupKeys() {
        val backup = validBackup().copy(
            profiles = validBackup().profiles?.map { it.copy(id = 7) },
            rules = listOf(RoutingRuleEntity(id = 5, type = "suffix", value = "ru", target = "DIRECT", position = 2))
        )
        val encoded = Json.parseToJsonElement(Json.encodeToString(backup)).jsonObject

        assertEquals(7, encoded.getValue("profiles").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.int)
        assertFalse(encoded.getValue("profiles").jsonArray.single().jsonObject.containsKey("position"))
        assertEquals(setOf("type", "value", "target"), encoded.getValue("rules").jsonArray.single().jsonObject.keys)
    }

    @Test
    fun chainsMustPointAtServersInTheSameBackup() {
        val server = requireNotNull(validBackup().profiles).single().copy(id = 7, selected = false)
        fun chainTo(exit: Long) = ProfileBackup(id = 9, name = "Chain", type = ProxyChain.TYPE, outboundJson = ProxyChain(ChainEntry.ByeDpi, exit).encode())

        assertTrue(BackupCodec.isValid(validBackup().copy(profiles = listOf(server, chainTo(7)))))
        assertFalse(BackupCodec.isValid(validBackup().copy(profiles = listOf(server, chainTo(8)))))
    }

    @Test
    fun boundedReadRejectsOversizedInput() {
        val input = ByteArrayInputStream(ByteArray(6))

        val error = runCatching {
            input.readBounded(maxBytes = 5)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(BackupCodec.decode("{not-json"))
    }

    private fun validBackup(): AppConfigBackup = AppConfigBackup(
        version = AppConfigBackup.CURRENT_VERSION,
        apps = mapOf("com.example.app" to "VPN"),
        settings = SettingsEntity(),
        profiles = listOf(
            ProfileBackup(
                name = "Example",
                type = "vless",
                link = VALID_LINK,
                outboundJson = """{"type":"vless","server":"example.com","port":443}""",
                selected = true
            )
        )
    )

    private companion object {
        const val VALID_LINK =
            "vless://00000000-0000-0000-0000-000000000000@example.com:443?security=tls#Example"
    }
}
