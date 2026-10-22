package io.github.yulbax.erkin.data

import androidx.room.Room
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.FetchedSubscription
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.RuleMatch
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigBackupRepositoryTest {
    private val database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
    private val repository = ConfigBackupRepository(
        database,
        ProfileRepository(database, database.profileDao(), NoSubscriptions, SilentLog)
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importingSettingsCarriesTheNewAppsChannel() = runBlocking {
        database.settingsDao().upsertSettings(SettingsEntity(mtu = 1500))
        val backup = AppConfigBackup(
            version = AppConfigBackup.CURRENT_VERSION,
            settings = SettingsEntity(mtu = 1280, newAppsConnectionType = ConnectionType.BYEDPI.wire)
        )

        repository.apply(backup, BackupSelection(settings = true, apps = false, profiles = false, rules = false))

        val settings = requireNotNull(database.settingsDao().getSettings())
        assertEquals(1280, settings.mtu)
        assertEquals(ConnectionType.BYEDPI, settings.newAppsConnectionType(ConnectionType.DIRECT))
    }

    @Test
    fun rulesRoundTripInOrderAndSkipDuplicates() = runBlocking {
        val dao = database.routingRuleDao()
        dao.append(RoutingRuleEntity(type = RuleMatch.DOMAIN_SUFFIX.wire, value = "ru", target = RuleTarget.DIRECT.wire))
        dao.append(RoutingRuleEntity(type = RuleMatch.IP_CIDR.wire, value = "1.1.1.1/32", target = RuleTarget.BLOCK.wire))
        val only = BackupSelection(settings = false, apps = false, profiles = false, rules = true)
        val exported = requireNotNull(BackupCodec.decode(repository.export(only)))
        assertEquals(listOf("ru", "1.1.1.1/32"), exported.rules?.map { it.value })

        dao.delete(dao.getAll().first())
        val result = repository.apply(exported, only)

        assertEquals(1, result.rulesAdded)
        assertEquals(1, result.rulesSkipped)
        assertEquals(listOf("1.1.1.1/32", "ru"), dao.getAll().map { it.value })
        val broken = exported.copy(rules = listOf(RoutingRuleEntity(type = RuleMatch.DOMAIN_SUFFIX.wire, value = "a,b", target = RuleTarget.DIRECT.wire)))
        assertFalse(BackupCodec.isValid(broken))
    }

    @Test
    fun chainsRoundTripWithTheirHopsRenumbered() = runBlocking {
        val profiles = ProfileRepository(database, database.profileDao(), NoSubscriptions, SilentLog)
        profiles.add("vless://id@entry.example:443?security=tls#Entry")
        profiles.add("vless://id@exit.example:443?security=tls#Exit")
        val (entry, exit) = database.profileDao().getAll()
        profiles.saveChain(null, "", ProxyChain(ChainEntry.Server(entry.id), exit.id))
        val only = BackupSelection(settings = false, apps = false, profiles = true, rules = false)
        val exported = requireNotNull(BackupCodec.decode(repository.export(only)))

        val fresh = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
        try {
            fresh.profileDao().append(ProfileEntity(name = "Other", type = "vless", link = "vless://id@other.example:443?security=tls#Other", outboundJson = "{}"))
            val target = ConfigBackupRepository(fresh, ProfileRepository(fresh, fresh.profileDao(), NoSubscriptions, SilentLog))
            val result = target.apply(exported, only)
            val imported = fresh.profileDao().getAll()
            val byName = imported.associateBy { it.name }
            val chain = (requireNotNull(byName["Entry → Exit"]).kind as ServerKind.Chain).chain

            assertEquals(3, result.profilesAdded)
            assertEquals(ProxyChain(ChainEntry.Server(requireNotNull(byName["Entry"]).id), requireNotNull(byName["Exit"]).id), chain)
            assertEquals(0, target.apply(exported, only).profilesAdded)
        } finally {
            fresh.close()
        }
    }

    private object NoSubscriptions : SubscriptionProfileSource {
        override suspend fun fetch(url: String): FetchedSubscription = FetchedSubscription(emptyList())
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }
}
