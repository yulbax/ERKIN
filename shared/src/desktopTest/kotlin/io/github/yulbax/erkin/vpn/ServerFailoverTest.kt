package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProbeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerFailoverTest {

    @Test
    fun theLadderReconnectsThreeTimesBeforeSwitchingAndStartsOverAfterwards() {
        val ladder = RecoveryLadder()
        val steps = List(8) { ladder.next() }

        val reconnect = RecoveryStep.RECONNECT
        assertEquals(listOf(reconnect, reconnect, reconnect, RecoveryStep.SWITCH_SERVER, reconnect, reconnect, reconnect, RecoveryStep.SWITCH_SERVER), steps)
    }

    @Test
    fun aWorkingVpnResetsTheLadder() {
        val ladder = RecoveryLadder()
        repeat(3) { ladder.next() }
        ladder.reset()

        assertEquals(RecoveryStep.RECONNECT, ladder.next())
    }

    @Test
    fun theFastestAnsweringServerWins() {
        val results = mapOf(1L to ProbeResult.NoAnswer, 2L to ProbeResult.Answered(240), 3L to ProbeResult.Answered(90))

        assertEquals(3L, ServerFailover.fastest(results))
        assertNull(ServerFailover.fastest(mapOf(1L to ProbeResult.NoAnswer)))
    }

    @Test
    fun switchesToTheFastestServerOfTheRefreshedSubscription() = runBlocking {
        val store = FakeStore()
        failover(store, """{"p1":0,"p2":240,"p3":90,"p9":10}""").switchToFastest()

        assertEquals(listOf(SUBSCRIPTION.url), store.refreshed)
        assertEquals(3L, store.selectedNow?.id)
    }

    @Test
    fun aLocalGroupSwitchesWithoutRefreshing() = runBlocking {
        val store = FakeStore(group = ServerGroupEntity(id = SUBSCRIPTION.id, name = "Mine"))
        failover(store, """{"p1":0,"p2":240,"p3":90,"p9":10}""").switchToFastest()

        assertEquals(emptyList<String>(), store.refreshed)
        assertEquals(3L, store.selectedNow?.id)
    }

    @Test
    fun keepsTheCurrentServerWhenNoServerAnswers() = runBlocking {
        val store = FakeStore()
        failover(store, """{"p1":0,"p2":0,"p3":0}""").switchToFastest()

        assertNull(store.selectedNow)
    }

    @Test
    fun aFailedRefreshStillPicksFromTheStoredList() = runBlocking {
        val store = FakeStore(refresh = ProfileOperationResult.FetchFailed(IllegalStateException("offline")))
        failover(store, """{"p1":0,"p2":70,"p3":90}""").switchToFastest()

        assertEquals(2L, store.selectedNow?.id)
    }

    private fun failover(store: FakeStore, results: String) =
        ServerFailover(store, ServerLatencyProbe(store) { _, _, _, _, _ -> results }, SilentLog)

    private class FakeStore(
        private val refresh: ProfileOperationResult = ProfileOperationResult.SubscriptionSynced(0, 0, 0, false),
        private val group: ServerGroupEntity = SUBSCRIPTION
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
        val SUBSCRIPTION = ServerGroupEntity(id = 7, url = "https://example.com/sub")
    }
}
