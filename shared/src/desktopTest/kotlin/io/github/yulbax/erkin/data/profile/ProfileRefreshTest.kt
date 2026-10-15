package io.github.yulbax.erkin.data.profile

import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ParsedProfile
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileRefreshTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ProfileRepository
    private val log = RecordingLog()
    private val subscription = FakeSubscription()

    @Before
    fun setUp() {
        database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
        repository = ProfileRepository(database, database.profileDao(), subscription, log)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rebuildsDescriptorsStoredBeforeASchemaChange() = runBlocking {
        database.profileDao().insert(
            ProfileEntity(
                name = "Amnezia",
                type = ProxyProtocol.AMNEZIAWG.wire,
                link = AWG_CONFIG,
                outboundJson = STALE_DESCRIPTOR
            )
        )

        repository.refreshDescriptors()

        val stored = database.profileDao().getAll().single()
        val proxy = kotlinx.serialization.json.Json.parseToJsonElement(stored.outboundJson).jsonObject
        assertEquals("wireguard", proxy.getValue("type").jsonPrimitive.content)
        assertEquals(25, proxy.getValue("persistent-keepalive").jsonPrimitive.content.toInt())
        assertEquals("aPresharedKey=", proxy.getValue("pre-shared-key").jsonPrimitive.content)
        assertEquals(4, proxy.getValue("amnezia-wg-option").jsonObject.getValue("jc").jsonPrimitive.content.toInt())
    }

    @Test
    fun movesAPlainConfStoredAsAmneziaWgOverToWireGuard() = runBlocking {
        database.profileDao().insert(
            ProfileEntity(
                name = "Home",
                type = ProxyProtocol.AMNEZIAWG.wire,
                link = PLAIN_CONF,
                outboundJson = """{"type":"awg","address":["10.8.1.2/32"],"private_key":"aPrivateKey=","peers":[]}"""
            )
        )

        repository.refreshDescriptors()

        val stored = database.profileDao().getAll().single()
        assertEquals(ProxyProtocol.WIREGUARD.wire, stored.type)
        val proxy = kotlinx.serialization.json.Json.parseToJsonElement(stored.outboundJson).jsonObject
        assertEquals("wireguard", proxy.getValue("type").jsonPrimitive.content)
        assertFalse(proxy.containsKey("amnezia-wg-option"))
        assertEquals("Home", stored.name)
    }

    @Test
    fun keepsUpToDateDescriptorsUntouched() = runBlocking {
        repository.add(AWG_CONFIG)
        val before = database.profileDao().getAll().single()

        repository.refreshDescriptors()

        assertEquals(before, database.profileDao().getAll().single())
    }

    @Test
    fun keepsProfilesReadableWhenTheStoredTypeIsUnknownToThisBuild() = runBlocking {
        database.profileDao().insert(
            ProfileEntity(
                name = "From a newer build",
                type = "quantumwg",
                link = "quantumwg://whatever",
                outboundJson = STALE_DESCRIPTOR
            )
        )

        val stored = database.profileDao().getAll().single()
        assertEquals("quantumwg", stored.type)
        assertNull(stored.protocol)

        repository.refreshDescriptors()

        assertEquals(stored, database.profileDao().getAll().single())
        assertTrue(log.warnings.single().contains("keep a stale descriptor"))
        assertTrue(repository.profileDiagnostics().single().contains("unknown to this build"))
    }

    @Test
    fun diagnosticsReportStaleAndFreshDescriptorsWithoutLeakingSecrets() = runBlocking {
        repository.add(AWG_CONFIG)
        database.profileDao().insert(
            ProfileEntity(name = "Stale", type = ProxyProtocol.AMNEZIAWG.wire, link = AWG_CONFIG, outboundJson = STALE_DESCRIPTOR)
        )

        val report = repository.profileDiagnostics()

        assertTrue(report.any { it.contains("fresh=true") })
        assertTrue(report.any { it.contains("fresh=false") })
        assertTrue(report.none { it.contains("aPrivateKey") || it.contains("vpn.example.com") })
    }

    private class RecordingLog : AppLog {
        val warnings = mutableListOf<String>()
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) {
            warnings += message
        }
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private class FakeSubscription : SubscriptionProfileSource {
        var links: List<String> = emptyList()
        override suspend fun fetch(url: String): List<ParsedProfile> = links.mapNotNull(LinkParser::parse)
    }

    private suspend fun storedSubscriptionServer(link: String): ProfileEntity {
        val parsed = requireNotNull(LinkParser.parse(link))
        val id = database.profileDao().insert(
            ProfileEntity(
                name = parsed.name,
                type = parsed.protocol.wire,
                link = parsed.link,
                outboundJson = parsed.proxyJson(),
                subscriptionUrl = SUBSCRIPTION_URL
            )
        )
        return database.profileDao().getAll().single { it.id == id }
    }

    private suspend fun refreshed(profile: ProfileEntity): ProfileEntity =
        database.profileDao().getAll().single { it.id == profile.id }

    @Test
    fun refreshFollowsTheSameServerWhenTheProviderRenamesIt() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany%20%7C%203%20GB%20left"
        )

        assertEquals(ProfileOperationResult.Success(affected = 1), repository.refreshSubscription(profile))

        val stored = refreshed(profile)
        assertTrue(stored.outboundJson.contains("b.example"))
        assertEquals("Germany", stored.name)
    }

    @Test
    fun refreshPicksUpNewCredentialsUnderTheSameName() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$OTHER_UUID@c.example:8443?security=tls&sni=c.example#Germany"
        )

        repository.refreshSubscription(profile)

        assertTrue(refreshed(profile).outboundJson.contains("c.example"))
    }

    @Test
    fun refreshNeverReplacesAServerTheSubscriptionNoLongerLists() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf("vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands")

        assertEquals(ProfileOperationResult.Success(affected = 0), repository.refreshSubscription(profile))

        assertEquals(profile, refreshed(profile))
    }

    @Test
    fun refreshLeavesAServerAloneWhenItsNameIsAmbiguous() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Germany",
            "vless://$UUID@c.example:443?security=tls&sni=c.example#Germany"
        )

        repository.refreshSubscription(profile)

        assertEquals(profile, refreshed(profile))
    }

    @Test
    fun addingAFileWithSeveralLinksImportsEachServer() = runBlocking {
        val body = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "not a link",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany"
        ).joinToString("\n")

        assertEquals(ProfileOperationResult.Success(affected = 2), repository.add(body))

        val stored = database.profileDao().getAll()
        assertEquals(listOf("Netherlands", "Germany"), stored.map { it.name })
        assertTrue(stored.all { it.subscriptionUrl.isEmpty() })
    }

    @Test
    fun addingTextWithoutAnyLinkIsRejected() = runBlocking {
        assertEquals(ProfileOperationResult.InvalidLink, repository.add("just some notes\nand more"))
        assertTrue(database.profileDao().getAll().isEmpty())
    }

    @Test
    fun readsAConfigFileAndRejectsOversizedOrBlankOnes() {
        assertEquals(AWG_CONFIG, repository.readConfigFile { AWG_CONFIG.byteInputStream() })
        assertNull(repository.readConfigFile { "  \n".byteInputStream() })
        assertNull(repository.readConfigFile { ByteArray(2 shl 20).inputStream() })
        assertNull(repository.readConfigFile { null })
    }

    @Test
    fun deletingTheLastServerMovesVpnAppsToDirect() = runBlocking {
        val id = database.profileDao().insert(
            ProfileEntity(name = "Only", type = ProxyProtocol.VLESS.wire, link = "vless://id@example.com:443", outboundJson = "{}")
        )
        database.appDao().upsertApps(
            listOf(
                App("org.vpn", "Vpn", isSystemApp = false, connectionType = ConnectionType.VPN),
                App("org.dpi", "Dpi", isSystemApp = false, connectionType = ConnectionType.BYEDPI)
            )
        )

        repository.delete(database.profileDao().getAll().single { it.id == id })

        assertEquals(ConnectionType.DIRECT, database.appDao().getApp("org.vpn")?.connectionType)
        assertEquals(ConnectionType.BYEDPI, database.appDao().getApp("org.dpi")?.connectionType)
    }

    private companion object {
        const val SUBSCRIPTION_URL = "https://sub.example/list"
        const val UUID = "11111111-2222-3333-4444-555555555555"
        const val OTHER_UUID = "66666666-7777-8888-9999-000000000000"

        const val STALE_DESCRIPTOR = """{"type":"wireguard","address":["10.8.1.2/32"],"private_key":"aPrivateKey=",""" +
            """"peers":[{"address":"vpn.example.com","port":51820,"public_key":"aPublicKey=",""" +
            """"pre_shared_key":"aPresharedKey=","allowed_ips":["0.0.0.0/0"],"persistent_keepalive_interval":"25-35"}]}"""

        val PLAIN_CONF = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = aPrivateKey=

            [Peer]
            PublicKey = aPublicKey=
            AllowedIPs = 0.0.0.0/0
            Endpoint = vpn.example.com:51820
        """.trimIndent()

        val AWG_CONFIG = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = aPrivateKey=
            Jc = 4

            [Peer]
            PublicKey = aPublicKey=
            PresharedKey = aPresharedKey=
            AllowedIPs = 0.0.0.0/0
            Endpoint = vpn.example.com:51820
            PersistentKeepalive = 25-35
        """.trimIndent()
    }
}
