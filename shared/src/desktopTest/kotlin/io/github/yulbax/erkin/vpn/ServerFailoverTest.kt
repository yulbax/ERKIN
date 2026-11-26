package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFailoverTest {

    @Test
    fun theLadderReconnectsOnceBeforeSwitchingAndStartsOverAfterwards() {
        val ladder = RecoveryLadder()
        val steps = List(4) { ladder.next() }

        val reconnect = RecoveryStep.RECONNECT
        assertEquals(listOf(reconnect, RecoveryStep.SWITCH_SERVER, reconnect, RecoveryStep.SWITCH_SERVER), steps)
    }

    @Test
    fun aWorkingVpnResetsTheLadder() {
        val ladder = RecoveryLadder()
        ladder.next()
        ladder.reset()

        assertEquals(RecoveryStep.RECONNECT, ladder.next())
    }

    @Test
    fun theFirstOtherServerOfTheGroupToAnswerTakesOver() = runBlocking {
        val store = FakeStore()

        assertEquals(true, failover(store, """{"p1":0,"p2":300,"p3":40,"p9":5}""").replace(testAgain = false))
        assertEquals(3L, store.selectedNow?.id)
        assertEquals(emptyList<String>(), store.refreshed)
    }

    @Test
    fun theSearchDoesNotWaitForTheSilentServersToTimeOut() = runBlocking {
        val store = FakeStore()
        val tester = ProxyLatencyTester { proxies, _, _, _, _, _ ->
            val tag = proxies.first().tag
            if (tag == "p3") Thread.sleep(SILENT_TIMEOUT_MS) else delay(40)
            """{"$tag":${if (tag == "p3") 0 else 40}}"""
        }
        val started = System.nanoTime()

        assertEquals(true, failoverWith(store, tester).replace(testAgain = false))
        assertEquals(2L, store.selectedNow?.id)
        assertTrue((System.nanoTime() - started) / 1_000_000 < SILENT_TIMEOUT_MS)
    }

    @Test
    fun nothingChangesWhenNoOtherServerAnswersOrTheServerIsNotInAGroup() = runBlocking {
        val store = FakeStore()
        assertEquals(false, failover(store, """{"p1":0,"p2":0,"p3":0}""").replace(testAgain = false))

        val ungrouped = FakeStore(group = null)
        assertEquals(false, failover(ungrouped, """{"p1":0,"p2":70}""").replace(testAgain = false))
        assertNull(store.selectedNow)
        assertNull(ungrouped.selectedNow)
    }

    @Test
    fun aServerThatAnsweredBeforeIsTestedAgainOnlyOnceAnotherOneAnswers() = runBlocking {
        val store = FakeStore()
        val tested = mutableListOf<String>()
        val tester = ProxyLatencyTester { proxies, _, _, _, _, _ ->
            val tag = proxies.first().tag
            tested += tag
            val ms = if (tag == "p1") (if (tested.any { it != "p1" }) 30 else 0) else 70
            """{"$tag":$ms}"""
        }

        assertEquals(false, failoverWith(store, tester).replace(testAgain = true))
        assertEquals("p1", tested.last())
        assertNull(store.selectedNow)
        assertEquals(true, failover(store, """{"p1":0,"p2":70,"p3":0}""").replace(testAgain = true))
        assertEquals(2L, store.selectedNow?.id)
    }

    @Test
    fun theLadderRefreshesTheSubscriptionBeforeLooking() = runBlocking {
        val store = FakeStore()
        failover(store, """{"p1":0,"p2":70,"p3":0}""").refreshAndReplace()

        assertEquals(listOf(SUBSCRIPTION.url), store.refreshed)
        assertEquals(2L, store.selectedNow?.id)
    }

    @Test
    fun aLocalGroupIsNotRefreshed() = runBlocking {
        val store = FakeStore(group = ServerGroupEntity(id = SUBSCRIPTION.id, name = "Mine"))
        failover(store, """{"p1":0,"p2":70,"p3":0}""").refreshAndReplace()

        assertEquals(emptyList<String>(), store.refreshed)
        assertEquals(2L, store.selectedNow?.id)
    }

    @Test
    fun aFailedRefreshStillLooksInTheStoredList() = runBlocking {
        val store = FakeStore(refresh = ProfileOperationResult.FetchFailed(IllegalStateException("offline")))
        failover(store, """{"p1":0,"p2":70,"p3":90}""").refreshAndReplace()

        assertEquals(2L, store.selectedNow?.id)
    }

    private fun failover(store: FakeStore, results: String): ServerFailover {
        val delays = Json.parseToJsonElement(results).jsonObject.mapValues { it.value.jsonPrimitive.int }
        return failoverWith(store) { proxies, _, _, _, _, _ ->
            val tag = proxies.first().tag
            val ms = delays[tag] ?: 0
            delay(if (ms > 0) ms.toLong() else NO_ANSWER_MS)
            """{"$tag":$ms}"""
        }
    }

    private fun failoverWith(store: FakeStore, tester: ProxyLatencyTester) =
        ServerFailover(store, ServerChecks(ServerLatencyProbe(store, tester = tester), ProbeResults(), SilentLog), SilentLog)

    private class FakeStore(
        private val refresh: ProfileOperationResult = ProfileOperationResult.SubscriptionSynced(0, 0, 0, false),
        private val group: ServerGroupEntity? = SUBSCRIPTION
    ) : VpnSessionStore {
        val refreshed = mutableListOf<String>()
        var selectedNow: ProfileEntity? = null
        private val profiles = listOf(
            ProfileEntity(id = 1, name = "dead", selected = true, groupId = SUBSCRIPTION.id),
            ProfileEntity(id = 2, name = "slow", groupId = SUBSCRIPTION.id),
            ProfileEntity(id = 3, name = "fast", groupId = SUBSCRIPTION.id),
            ProfileEntity(id = 9, name = "other group", groupId = 99)
        )

        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(profiles.first(), profiles, SettingsEntity(), emptyList())
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedGroup() = group
        override suspend fun refreshSubscription(url: String): ProfileOperationResult = refresh.also { refreshed += url }
        override suspend fun refreshDueSubscriptions() = Unit
        override suspend fun select(profile: ProfileEntity) {
            selectedNow = profile
        }
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }

    private companion object {
        const val NO_ANSWER_MS = 100L
        const val SILENT_TIMEOUT_MS = 2_000L
        val SUBSCRIPTION = ServerGroupEntity(id = 7, url = "https://example.com/sub")
    }
}
