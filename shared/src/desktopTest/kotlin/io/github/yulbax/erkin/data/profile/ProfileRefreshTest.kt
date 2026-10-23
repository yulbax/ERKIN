package io.github.yulbax.erkin.data.profile

import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.proxy.LinkParser
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
    fun aConfigFromAFileIsNamedAfterTheFile() = runBlocking {
        repository.add(AWG_CONFIG, fileName = "Home server.conf")

        assertEquals("Home server", database.profileDao().getAll().single().name)
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


    @Test
    fun chainsAreServersThatFollowTheirHopsAndDisappearWithThem() = runBlocking {
        repository.add("vless://id@entry.example:443?security=tls#Entry")
        repository.add("vless://id@exit.example:443?security=tls#Exit")
        val (entry, exit) = database.profileDao().getAll()

        assertFalse(repository.saveChain(null, "", ProxyChain(ChainEntry.Server(exit.id), exit.id)))
        assertTrue(repository.saveChain(null, "", ProxyChain(ChainEntry.Server(entry.id), exit.id)))
        assertTrue(repository.saveChain(null, "Through ByeDPI", ProxyChain(ChainEntry.ByeDpi, exit.id)))
        val chains = database.profileDao().getAll().filter { it.kind is ServerKind.Chain }
        assertEquals(listOf("Entry → Exit", "Through ByeDPI"), chains.map { it.name })
        assertEquals(ProxyChain(ChainEntry.Server(entry.id), exit.id), (chains.first().kind as ServerKind.Chain).chain)

        repository.refreshDescriptors()
        assertEquals(chains, database.profileDao().getAll().filter { it.kind is ServerKind.Chain })

        val middle = repository.run {
            add("vless://id@middle.example:443?security=tls#Middle")
            database.profileDao().getAll().single { it.name == "Middle" }
        }
        assertTrue(repository.saveChain(null, "", ProxyChain(listOf(ChainEntry.ByeDpi, ChainEntry.Server(entry.id), ChainEntry.Server(middle.id)), exit.id)))
        assertEquals("ByeDPI → Entry → Middle → Exit", database.profileDao().getAll().last().name)
        repository.delete(listOf(middle))
        assertEquals(chains.map { it.name }, database.profileDao().getAll().filter { it.kind is ServerKind.Chain }.map { it.name })

        repository.delete(listOf(entry))
        assertEquals(listOf("Exit", "Through ByeDPI"), database.profileDao().getAll().map { it.name })
        repository.delete(listOf(exit))
        assertTrue(database.profileDao().getAll().isEmpty())
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
        var info = SubscriptionInfo()
        var refusal: SubscriptionRefusal? = null
        override suspend fun fetch(url: String): FetchedSubscription {
            refusal?.let { throw SubscriptionRefusedException(it) }
            return FetchedSubscription(links.mapNotNull(LinkParser::parse), info)
        }
    }

    private suspend fun storedSubscriptionServer(link: String, selected: Boolean = false): ProfileEntity {
        val parsed = requireNotNull(LinkParser.parse(link))
        val subscriptionId = database.subscriptionDao().obtain(SUBSCRIPTION_URL).id
        val id = database.profileDao().append(
            ProfileEntity(
                name = parsed.name,
                type = parsed.protocol.wire,
                link = parsed.link,
                outboundJson = parsed.proxyJson(),
                selected = selected,
                subscriptionId = subscriptionId
            )
        )
        return database.profileDao().getAll().single { it.id == id }
    }

    private suspend fun names(): List<String> = database.profileDao().getAll().map { it.name }

    private suspend fun refreshed(profile: ProfileEntity): ProfileEntity =
        database.profileDao().getAll().single { it.id == profile.id }

    @Test
    fun refreshFollowsTheSameServerWhenTheProviderRenamesIt() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany%20%7C%203%20GB%20left"
        )

        assertEquals(
            ProfileOperationResult.SubscriptionSynced(added = 1, updated = 1, removed = 0, selectedDropped = false),
            repository.refreshSubscription(SUBSCRIPTION_URL)
        )

        val stored = refreshed(profile)
        assertTrue(stored.outboundJson.contains("b.example"))
        assertEquals("Germany", stored.name)
        assertEquals(listOf("Netherlands", "Germany"), names())
    }

    @Test
    fun refreshPicksUpNewCredentialsUnderTheSameName() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$OTHER_UUID@c.example:8443?security=tls&sni=c.example#Germany"
        )

        repository.refreshSubscription(SUBSCRIPTION_URL)

        assertTrue(refreshed(profile).outboundJson.contains("c.example"))
    }

    @Test
    fun refreshDropsServersTheSubscriptionNoLongerLists() = runBlocking {
        storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = listOf("vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands")

        assertEquals(
            ProfileOperationResult.SubscriptionSynced(added = 1, updated = 0, removed = 1, selectedDropped = false),
            repository.refreshSubscription(SUBSCRIPTION_URL)
        )

        assertEquals(listOf("Netherlands"), names())
    }

    @Test
    fun refreshKeepsTheSelectedServerAndChainHopsItNoLongerLists() = runBlocking {
        val selected = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany", selected = true)
        val hop = storedSubscriptionServer("vless://$UUID@c.example:443?security=tls&sni=c.example#France")
        repository.add("vless://id@exit.example:443?security=tls#Exit")
        val exit = database.profileDao().getAll().single { it.name == "Exit" }
        assertTrue(repository.saveChain(null, "Chain", ProxyChain(ChainEntry.Server(hop.id), exit.id)))
        subscription.links = listOf("vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands")

        val result = repository.refreshSubscription(SUBSCRIPTION_URL) as ProfileOperationResult.SubscriptionSynced

        assertTrue(result.selectedDropped)
        assertEquals(0, result.removed)
        assertEquals(selected, refreshed(selected).copy(position = selected.position))
        assertEquals(listOf("Netherlands", "Germany", "France", "Exit", "Chain"), names())
    }

    @Test
    fun refreshLeavesAServerAloneWhenItsNameIsAmbiguous() = runBlocking {
        val profile = storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany", selected = true)
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Germany",
            "vless://$UUID@c.example:443?security=tls&sni=c.example#Germany"
        )

        repository.refreshSubscription(SUBSCRIPTION_URL)

        assertEquals(profile.outboundJson, refreshed(profile).outboundJson)
        assertEquals(3, database.profileDao().getAll().size)
    }

    @Test
    fun addingASubscriptionAgainUpdatesItInPlace() = runBlocking {
        repository.add("vless://id@before.example:443?security=tls#Before")
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany"
        )
        repository.add(SUBSCRIPTION_URL)
        repository.add("vless://id@after.example:443?security=tls#After")
        subscription.links = listOf(
            "vless://$UUID@c.example:443?security=tls&sni=c.example#France",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany"
        )

        repository.add(SUBSCRIPTION_URL)

        assertEquals(listOf("Before", "France", "Germany", "After"), names())
    }

    @Test
    fun subscriptionDetailsAreKeptUntilItsLastServerIsDeleted() = runBlocking {
        subscription.links = listOf("vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands")
        subscription.info = SubscriptionInfo(title = "Provider", usage = SubscriptionUsage(1, 2, 10, 1_900_000_000))
        repository.add(SUBSCRIPTION_URL)
        val id = requireNotNull(database.subscriptionDao().find(SUBSCRIPTION_URL)).id
        repository.renameSubscription(id, "  Mine ")

        val stored = requireNotNull(database.subscriptionDao().get(id))
        assertEquals("Provider", stored.title)
        assertEquals("Mine", stored.displayName)
        assertEquals(SubscriptionUsage(1, 2, 10, 1_900_000_000), stored.usage)

        repository.delete(database.profileDao().getAll())

        assertNull(database.subscriptionDao().get(id))
    }

    @Test
    fun aSubscriptionRemembersTheServerLastPickedInIt() = runBlocking {
        subscription.links = listOf(
            "vless://$UUID@a.example:443?security=tls&sni=a.example#Netherlands",
            "vless://$UUID@b.example:443?security=tls&sni=b.example#Germany"
        )
        repository.add(SUBSCRIPTION_URL)
        repository.add("vless://id@other.example:443?security=tls#Other")
        val (_, germany, other) = database.profileDao().getAll()

        repository.select(germany)
        repository.select(other)

        assertEquals(germany.id, database.subscriptionDao().find(SUBSCRIPTION_URL)?.currentId)
        assertEquals(other.id, database.profileDao().getSelected()?.id)
    }

    @Test
    fun anEmptySubscriptionLeavesTheStoredServersAlone() = runBlocking {
        storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.links = emptyList()

        assertEquals(ProfileOperationResult.EmptySubscription, repository.refreshSubscription(SUBSCRIPTION_URL))

        assertEquals(listOf("Germany"), names())
    }

    @Test
    fun aDeviceLimitRefusalLeavesTheStoredServersAlone() = runBlocking {
        storedSubscriptionServer("vless://$UUID@b.example:443?security=tls&sni=b.example#Germany")
        subscription.refusal = SubscriptionRefusal.DEVICE_LIMIT

        assertEquals(
            ProfileOperationResult.Refused(SubscriptionRefusal.DEVICE_LIMIT),
            repository.refreshSubscription(SUBSCRIPTION_URL)
        )

        assertEquals(listOf("Germany"), names())
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
        assertTrue(stored.all { it.subscriptionId == null })
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

        repository.delete(listOf(database.profileDao().getAll().single { it.id == id }))

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
