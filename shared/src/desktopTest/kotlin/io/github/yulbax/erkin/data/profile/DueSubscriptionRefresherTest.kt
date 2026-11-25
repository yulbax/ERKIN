package io.github.yulbax.erkin.data.profile

import androidx.room.Room
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DueSubscriptionRefresherTest {
    private lateinit var database: AppDatabase
    private lateinit var refresher: DueSubscriptionRefresher
    private val source = RecordingSource()
    private var now = 1_000 * HOUR

    @Before
    fun setUp() {
        database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>(), appsCanBypass = true)
        refresher = DueSubscriptionRefresher(ProfileRepository(database, database.profileDao(), source, SilentLog), SilentLog) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun providerHoursAreTheInterval() {
        assertEquals(6, SubscriptionSchedule.intervalHours(6))
        assertEquals(720, SubscriptionSchedule.intervalHours(720))
    }

    @Test
    fun withoutProviderHoursTheIntervalIsADay() {
        assertEquals(24, SubscriptionSchedule.intervalHours(null))
    }

    @Test
    fun theIntervalIsNeverShorterThanAnHour() {
        assertEquals(1, SubscriptionSchedule.intervalHours(0))
        assertEquals(1, SubscriptionSchedule.intervalHours(-5))
    }

    @Test
    fun aNeverRefreshedSubscriptionIsDue() {
        assertTrue(SubscriptionSchedule.isDue(subscription(updatedAt = 0), now, null))
    }

    @Test
    fun aSubscriptionIsDueOnceItsIntervalHasPassed() {
        val group = subscription(updatedAt = now - 5 * HOUR, updateHours = 6)
        assertFalse(SubscriptionSchedule.isDue(group, now, null))
        assertTrue(SubscriptionSchedule.isDue(group, now + HOUR, null))
    }

    @Test
    fun theDefaultIntervalAppliesWithoutProviderHours() {
        assertFalse(SubscriptionSchedule.isDue(subscription(updatedAt = now - 23 * HOUR), now, null))
        assertTrue(SubscriptionSchedule.isDue(subscription(updatedAt = now - 24 * HOUR), now, null))
    }

    @Test
    fun aFailedAttemptHoldsTheSubscriptionBackForAnHour() {
        val group = subscription(updatedAt = 0)
        assertFalse(SubscriptionSchedule.isDue(group, now, now - 59 * 60_000))
        assertTrue(SubscriptionSchedule.isDue(group, now, now - HOUR))
    }

    @Test
    fun aLocalGroupIsNeverDue() {
        assertFalse(SubscriptionSchedule.isDue(ServerGroupEntity(id = 1, name = "Mine"), now, null))
    }

    @Test
    fun onlyDueSubscriptionsAreRefreshedOneAfterAnother() = runBlocking {
        store("https://a.example/sub", updatedAt = 0)
        store("https://b.example/sub", updatedAt = now - HOUR, updateHours = 6)
        store("https://c.example/sub", updatedAt = now - 30 * HOUR)

        refresher.refreshDue()

        assertEquals(listOf("https://a.example/sub", "https://c.example/sub"), source.fetched)
        assertEquals(1, source.maxConcurrent)
    }

    @Test
    fun aFailureDoesNotStopTheOthersAndIsNotRetriedForAnHour() = runBlocking {
        store("https://a.example/sub", updatedAt = 0)
        store("https://b.example/sub", updatedAt = 0)
        source.failing += "https://a.example/sub"

        refresher.refreshDue()
        assertEquals(listOf("https://a.example/sub", "https://b.example/sub"), source.fetched)

        source.fetched.clear()
        now += 30 * 60_000
        refresher.refreshDue()
        assertEquals(emptyList<String>(), source.fetched)

        now += HOUR
        source.failing.clear()
        refresher.refreshDue()
        assertEquals(listOf("https://a.example/sub"), source.fetched)
    }

    @Test
    fun aRefreshedSubscriptionIsNotDueAgain() = runBlocking {
        store("https://a.example/sub", updatedAt = 0)
        refresher.refreshDue()
        source.fetched.clear()

        refresher.refreshDue()

        assertEquals(emptyList<String>(), source.fetched)
    }

    private fun subscription(updatedAt: Long, updateHours: Int? = null) =
        ServerGroupEntity(id = 1, url = "https://a.example/sub", updatedAt = updatedAt, updateHours = updateHours)

    private suspend fun store(url: String, updatedAt: Long, updateHours: Int? = null) {
        val groups = database.serverGroupDao()
        val group = groups.obtain(url)
        val parsed = requireNotNull(LinkParser.parse(LINK))
        database.profileDao().append(
            ProfileEntity(name = parsed.name, type = parsed.protocol.wire, link = "${parsed.link}&u=${group.id}", outboundJson = parsed.proxyJson(), groupId = group.id)
        )
        groups.update(group.copy(updatedAt = updatedAt, updateHours = updateHours))
    }

    private inner class RecordingSource : SubscriptionProfileSource {
        val fetched = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        var maxConcurrent = 0
        private var running = 0

        override suspend fun fetch(url: String): FetchedSubscription {
            running++
            maxConcurrent = maxOf(maxConcurrent, running)
            try {
                fetched += url
                if (url in failing) error("offline")
                return FetchedSubscription(listOfNotNull(LinkParser.parse(LINK)))
            } finally {
                running--
            }
        }
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val LINK = "vless://11111111-2222-3333-4444-555555555555@a.example:443?security=tls&sni=a.example#Riga"
    }
}
