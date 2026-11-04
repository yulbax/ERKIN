package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.vpn.core.ProxyDelay
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
        val delays = mapOf(1L to ProxyDelay.Failed, 2L to ProxyDelay.Measured(240), 3L to ProxyDelay.Measured(90))

        assertEquals(3L, ServerFailover.fastest(delays))
        assertNull(ServerFailover.fastest(mapOf(1L to ProxyDelay.Failed)))
    }

    @Test
    fun switchesToTheFastestServerOfTheRefreshedSubscription() = runBlocking {
        val store = FakeStore()
        failover(store, """{"p1":0,"p2":240,"p3":90,"p9":10}""").switchToFastest()

        assertEquals(listOf(SUBSCRIPTION.url), store.refreshed)
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

    private class FakeStore(private val refresh: ProfileOperationResult = ProfileOperationResult.SubscriptionSynced(0, 0, 0, false)) : VpnSessionStore {
        val refreshed = mutableListOf<String>()
        var selectedNow: ProfileEntity? = null
        private val profiles = listOf(
            ProfileEntity(id = 1, name = "dead", selected = true, subscriptionId = SUBSCRIPTION.id),
            ProfileEntity(id = 2, name = "slow", subscriptionId = SUBSCRIPTION.id),
            ProfileEntity(id = 3, name = "fast", subscriptionId = SUBSCRIPTION.id),
            ProfileEntity(id = 9, name = "other subscription", subscriptionId = 99)
        )

        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(profiles.first(), profiles, SettingsEntity(), emptyList())
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedSubscription() = SUBSCRIPTION
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
        val SUBSCRIPTION = SubscriptionEntity(id = 7, url = "https://example.com/sub")
    }
}
