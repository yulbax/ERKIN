package io.github.yulbax.erkin.data.byedpi

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.vpn.SiteListPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ByeDpiSiteListsTest {
    private val database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFreshInstallEnablesTheListsOfItsLanguage() = runBlocking {
        assertEquals(listOf("YouTube", "Google Video"), ByeDpiSiteLists(database) { "ru" }.active().map { it.name })

        val turkish = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
        try {
            assertEquals(listOf("Discord", "Türkiye"), ByeDpiSiteLists(turkish) { "tr" }.active().map { it.name })
        } finally {
            turkish.close()
        }
    }

    @Test
    fun anUpgradeKeepsCheckingYouTubeAndGoogleVideo() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            AppDatabase.MIGRATIONS.single { it.startVersion == 24 }.migrate(connection)
            val active = mutableListOf<String>()
            connection.prepare("SELECT preset FROM byedpi_site_lists WHERE active = 1 ORDER BY id").use { while (it.step()) active += it.getText(0) }

            assertEquals(listOf(SiteListPreset.YOUTUBE.wire, SiteListPreset.GOOGLE_VIDEO.wire), active)
        } finally {
            connection.close()
        }
    }

    @Test
    fun anEditedBuiltInListIsMarkedAndResetsToItsOriginalSites() = runBlocking {
        val lists = ByeDpiSiteLists(database) { "en" }
        val youtube = lists.lists.first().first { it.name == "YouTube" }

        lists.edit(youtube.id, SiteListDraft("YouTube", "youtube.com\nhttps://m.youtube.com/watch\nnot a host"))
        val edited = lists.lists.first().first { it.id == youtube.id }
        assertTrue(edited.resettable)
        assertEquals(listOf("youtube.com", "m.youtube.com"), edited.sites)

        lists.reset(youtube.id)
        val reset = lists.lists.first().first { it.id == youtube.id }
        assertFalse(reset.resettable)
        assertEquals(SiteListPreset.YOUTUBE.sites, reset.sites)

        lists.edit(youtube.id, SiteListDraft.of(reset))
        assertFalse(lists.lists.first().first { it.id == youtube.id }.resettable)
    }

    @Test
    fun theLastEnabledListStaysEnabled() = runBlocking {
        val lists = ByeDpiSiteLists(database) { "en" }
        lists.active()
        lists.lists.first().filter { it.active && it.name != "YouTube" }.forEach { lists.setActive(it.id, false) }
        lists.add(SiteListDraft("Mine", "example.com"))
        val mine = lists.lists.first().first { it.name == "Mine" }
        val youtube = lists.lists.first().first { it.name == "YouTube" }
        lists.setActive(youtube.id, false)
        lists.delete(mine.id)

        assertEquals(listOf("Mine"), lists.active().map { it.name })
    }

    @Test
    fun customAndEditedListsSurviveABackup() = runBlocking {
        val source = ByeDpiSiteLists(database) { "en" }
        source.add(SiteListDraft("Mine", "example.com example.org"))
        val discord = source.lists.first().first { it.name == "Discord" }
        source.edit(discord.id, SiteListDraft("Discord", "discord.com"))
        source.setActive(discord.id, true)
        val backup = source.backup()

        val fresh = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
        try {
            val target = ByeDpiSiteLists(fresh) { "tr" }
            fresh.transaction { target.restoreWithinTransaction(backup) }
            val restored = target.lists.first()

            assertEquals(listOf("YouTube", "Google Video", "Discord", "Mine"), restored.filter { it.active }.map { it.name })
            assertEquals(listOf("discord.com"), restored.first { it.name == "Discord" }.sites)
            assertEquals(listOf("example.com", "example.org"), restored.first { it.name == "Mine" }.sites)
        } finally {
            fresh.close()
        }
    }
}
