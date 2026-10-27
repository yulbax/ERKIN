package io.github.yulbax.erkin.data.byedpi

import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.vpn.SiteResult
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.TestNetwork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByeDpiHistoryTest {
    private val database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
    private val settings = SettingsRepository(database, database.settingsDao())
    private var clock = 0L
    private val history = ByeDpiHistory(database, settings) { ++clock }

    @After
    fun close() = database.close()

    @Test
    fun applyingArgumentsStoresThemAndRecordsTheEffectiveLine() = runBlocking {
        history.apply("  -d1 -s1  ")
        history.apply("")

        assertEquals("", settings.settings.first().byeDpiArgs)
        assertEquals(
            listOf(ByeDpiArgs.effective(""), "-d1 -s1"),
            history.entries.first().map { it.args }
        )
    }

    @Test
    fun reusingArgumentsKeepsTheirPlaceInsteadOfDuplicating() = runBlocking {
        history.apply("-a")
        history.apply("-b")
        history.apply("-a")

        assertEquals(listOf("-b", "-a"), history.entries.first().map { it.args })
    }

    @Test
    fun onlyRecentUnpinnedArgumentsAreKept() = runBlocking {
        history.apply("-pinned")
        history.setPinned(history.entries.first().single().id, true)
        repeat(ByeDpiHistory.MAX_UNPINNED + 5) { history.apply("-s$it") }

        val entries = history.entries.first()
        assertEquals(ByeDpiHistory.MAX_UNPINNED + 1, entries.size)
        assertEquals("-pinned", entries.first().args)
        assertTrue(entries.none { it.args == "-s0" })
    }

    @Test
    fun trimmingNeverDropsTheActiveArguments() = runBlocking {
        history.apply("-active")
        repeat(ByeDpiHistory.MAX_UNPINNED + 5) { history.remember("-s$it") }

        val entries = history.entries.first().map { it.args }
        assertTrue("-active" in entries)
        assertEquals(ByeDpiHistory.MAX_UNPINNED + 1, entries.size)
    }

    @Test
    fun aNewTestReplacesTheOldOneOnTheSameNetworkOnly() = runBlocking {
        history.apply("-d1")
        history.saveResults("-d1", TestNetwork.Wifi, listOf(SiteResult("a.test", "Quick", true), SiteResult("b.test", "Quick", false)))
        history.saveResults("-d1", TestNetwork.Cellular("Op"), listOf(SiteResult("a.test", "Quick", false)))
        history.saveResults("-d1", TestNetwork.Wifi, listOf(SiteResult("c.test", "Quick", true)))

        val byNetwork = history.results.first().groupBy { it.network }.mapValues { (_, rows) -> rows.map { it.host } }
        assertEquals(mapOf("wifi" to listOf("c.test"), "cellular:Op" to listOf("a.test")), byNetwork)

        history.delete(history.entries.first().single().id)
        assertTrue(history.results.first().isEmpty())
    }

    @Test
    fun networksSurviveTheirWireForm() {
        listOf(TestNetwork.Wifi, TestNetwork.Ethernet, TestNetwork.Cellular("MTS"), TestNetwork.Unknown)
            .forEach { assertEquals(it, TestNetwork.fromWire(it.wire)) }
    }
}
