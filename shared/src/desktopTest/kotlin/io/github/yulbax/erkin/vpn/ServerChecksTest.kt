package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.data.SettingsEntity
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProfileOperationResult
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerChecksTest {

    @Test
    fun aFailedMeasurementIsNotReportedAsEveryServerUnreachable() = runBlocking {
        val results = ProbeResults()
        val checks = ServerChecks(ServerLatencyProbe(Store) { _, _, _, _, _, _ -> error("core is down") }, results, SilentLog)

        assertNull(checks.check(listOf(1L, 2L)))
        assertEquals(emptyMap<Long, Any>(), results.results.value)
    }

    @Test
    fun forgettingTheBackgroundKeepsWhatATestRunMeasured() = runBlocking {
        val results = ProbeResults()
        val checks = ServerChecks(ServerLatencyProbe(Store) { proxies, _, _, _, _, _ ->
            val tag = proxies.single().tag
            """{"$tag":40}"""
        }, results, SilentLog)
        checks.check(listOf(1L, 2L))
        checks.run(listOf(2L)).await()
        checks.forgetBackground()

        assertEquals(setOf(2L), results.results.value.keys)
    }

    @Test
    fun onlyAServerFromATestRunCanBeTheFastest() = runBlocking {
        val results = ProbeResults()
        val checks = ServerChecks(ServerLatencyProbe(Store) { proxies, _, _, _, _, _ ->
            val tag = proxies.single().tag
            """{"$tag":${if (tag == "p1") 20 else 60}}"""
        }, results, SilentLog)

        checks.check(listOf(1L))
        assertNull(ProbeResults.fastest(results.results.value, results.compared.value))

        checks.run(listOf(2L)).await()
        assertEquals(setOf(2L), results.compared.value)
        assertEquals(2L, ProbeResults.fastest(results.results.value, results.compared.value))
    }

    private object Store : VpnSessionStore {
        private val servers = (1L..2L).map { ProfileEntity(id = it, name = "s$it") }
        override suspend fun refreshProfiles() = Unit
        override suspend fun load() = SessionInputs(servers.first(), servers, SettingsEntity(), emptyList())
        override fun routingChanges(): Flow<Unit> = emptyFlow()
        override fun byeDpiArgsChanges(): Flow<String> = emptyFlow()
        override suspend fun selectedGroup(): ServerGroupEntity? = null
        override suspend fun refreshSubscription(url: String): ProfileOperationResult = ProfileOperationResult.EmptySubscription
        override suspend fun refreshDueSubscriptions() = Unit
        override suspend fun select(profile: ProfileEntity) = Unit
    }

    private object SilentLog : AppLog {
        override fun i(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, t: Throwable?) = Unit
        override fun e(tag: String, message: String, t: Throwable?) = Unit
    }
}
