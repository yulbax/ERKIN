package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerLatencyProbeTest {

    @Test
    fun mapsServerTagsToProfilesAndFailuresToFailed() {
        val delays = ServerLatencyProbe.parse("""{"p1":85,"p2":-1,"p3":0,"health":40}""")
        assertEquals(mapOf(1L to ProxyDelay.Measured(85), 2L to ProxyDelay.Failed, 3L to ProxyDelay.Failed), delays)
    }

    @Test
    fun aChainIsMeasuredThroughAllItsHops() = runBlocking {
        val tested = mutableListOf<EngineProxy>()
        val probe = probe(byeDpiPort = 1080, tested) { """{"p1":50,"p2":80,"p9h1":60,"p9":140}""" }

        val delays = probe.measure()

        assertEquals(mapOf(1L to ProxyDelay.Measured(50), 2L to ProxyDelay.Measured(80), 9L to ProxyDelay.Measured(140)), delays)
        assertEquals(listOf("p1", "p2", "p9h1", "p9"), tested.map { it.tag })
        assertEquals("p9h1", tested.single { it.tag == "p9" }.dialerProxy)
    }

    @Test
    fun withoutByeDpiRunningAChainSkipsItsByeDpiHopAndAFailureOnlyMeansItNeedsTheConnection() = runBlocking {
        val tested = mutableListOf<EngineProxy>()
        var port: Int? = -1
        val probe = ServerLatencyProbe(STORE, { 0 }) { proxies, _, _, _, byeDpiPort ->
            tested += proxies
            port = byeDpiPort
            """{"p1":0,"p2":80,"p9h1":0,"p9":0}"""
        }

        val delays = probe.measure()

        assertNull(port)
        assertNull(tested.single { it.tag == "p9h1" }.dialerProxy)
        assertEquals(ProxyDelay.Failed, delays[1])
        assertEquals(ProxyDelay.NeedsConnection, delays[9])
    }

    @Test
    fun whileByeDpiRunsAFailedChainIsReallyDown() = runBlocking {
        val tested = mutableListOf<EngineProxy>()
        val delays = probe(byeDpiPort = 1080, tested) { """{"p9h1":0,"p9":0}""" }.measure(setOf(9))

        assertEquals(BYEDPI_PROXY_TAG, tested.single { it.tag == "p9h1" }.dialerProxy)
        assertEquals(mapOf(9L to ProxyDelay.Failed), delays)
    }

    private fun probe(byeDpiPort: Int, tested: MutableList<EngineProxy>, results: () -> String) =
        ServerLatencyProbe(STORE, { byeDpiPort }) { proxies, _, _, _, _ ->
            tested += proxies
            results()
        }

    private object STORE : VpnSessionStore {
        private val profiles = listOf(
            ProfileEntity(id = 1, name = "a"),
            ProfileEntity(id = 2, name = "b"),
            ProfileEntity(id = 9, name = "chain", type = ProxyChain.TYPE, outboundJson = ProxyChain(listOf(ChainEntry.ByeDpi, ChainEntry.Server(1)), 2).encode())
        )

        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(profiles.first(), profiles, SettingsEntity(), emptyList())
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedGroup(): ServerGroupEntity? = null
        override suspend fun refreshSubscription(url: String): ProfileOperationResult = ProfileOperationResult.EmptySubscription
        override suspend fun select(profile: ProfileEntity) = Unit
    }
}
