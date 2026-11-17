package io.github.yulbax.erkin.data

import androidx.room.Room
import io.github.yulbax.erkin.data.byedpi.ByeDpiSiteLists
import io.github.yulbax.erkin.data.profile.FetchedSubscription
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.SubscriptionProfileSource
import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRulesTableTest {
    private val database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>(), appsCanBypass = true)
    private val dao = database.appDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingAnAppThatAlreadyHasARowKeepsItsSettings() = runBlocking {
        dao.addApps(listOf(App("a.b", "A", isSystemApp = false, connectionType = ConnectionType.BYEDPI, serverId = 7)))

        dao.addApps(listOf(App("a.b", "A", isSystemApp = false, connectionType = ConnectionType.VPN)))

        val kept = requireNotNull(dao.getApp("a.b"))
        assertEquals(ConnectionType.BYEDPI, kept.connectionType)
        assertEquals(7L, kept.serverId)
    }

    @Test
    fun aBackupAppWithoutARowGetsOneWithTheBackupsSettings() = runBlocking {
        val repository = ConfigBackupRepository(
            database,
            ProfileRepository(database, database.profileDao(), NoSubscriptions, SilentLog),
            ByeDpiSiteLists(database),
            appsCanBypass = true
        )
        val backup = AppConfigBackup(version = AppConfigBackup.CURRENT_VERSION, apps = mapOf("org.later" to ConnectionType.BYEDPI.wire))

        val result = repository.apply(backup, BackupSelection(settings = false, apps = true, profiles = false, rules = false))

        assertEquals(1, result.deferred)
        val created = requireNotNull(dao.getApp("org.later"))
        assertEquals("org.later", created.name)
        assertEquals(ConnectionType.BYEDPI, created.connectionType)
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
