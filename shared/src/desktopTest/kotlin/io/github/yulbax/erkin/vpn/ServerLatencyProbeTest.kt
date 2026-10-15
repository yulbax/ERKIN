package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerLatencyProbeTest {
    @Test
    fun mapsServerTagsToProfilesAndFailuresToFailed() {
        val delays = ServerLatencyProbe.parse("""{"p1":85,"p2":-1,"p3":0,"health":40}""")
        assertEquals(mapOf(1L to ProxyDelay.Measured(85), 2L to ProxyDelay.Failed, 3L to ProxyDelay.Failed), delays)
    }
}
