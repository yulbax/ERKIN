package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ByeDpiReachabilityTest {

    @Test
    fun qualityFollowsTheShareOfOpenedSites() {
        assertEquals(ByeDpiQuality.GOOD, ByeDpiReachability(8, 10).quality)
        assertEquals(ByeDpiQuality.GOOD, ByeDpiReachability(9, 10).quality)
        assertEquals(ByeDpiQuality.FAIR, ByeDpiReachability(4, 10).quality)
        assertEquals(ByeDpiQuality.FAIR, ByeDpiReachability(7, 10).quality)
        assertEquals(ByeDpiQuality.POOR, ByeDpiReachability(3, 10).quality)
        assertEquals(ByeDpiQuality.POOR, ByeDpiReachability(0, 0).quality)
    }
}
