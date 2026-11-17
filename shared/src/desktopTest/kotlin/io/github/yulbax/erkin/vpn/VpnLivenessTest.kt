package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnLivenessTest {

    private fun actionsOf(vararg results: ProbeResult): List<LivenessAction> {
        var counters = LivenessCounters()
        return results.map { result ->
            val step = counters.after(result)
            counters = step.counters
            step.action
        }
    }

    @Test
    fun theFourthFailureInARowRecoversTheVpn() {
        val actions = actionsOf(*Array(4) { ProbeResult.NoAnswer })

        assertEquals(
            listOf(LivenessAction.NONE, LivenessAction.NONE, LivenessAction.NONE, LivenessAction.VPN_DOWN),
            actions
        )
    }

    @Test
    fun aRejectedLoginStopsTheVpnAtOnce() {
        val actions = actionsOf(ProbeResult.NoAnswer, ProbeResult.LoginRejected)

        assertEquals(listOf(LivenessAction.NONE, LivenessAction.LOGIN_REJECTED), actions)
    }

    @Test
    fun anAnswerResetsTheFailures() {
        val failures = actionsOf(
            ProbeResult.NoAnswer, ProbeResult.NoAnswer, ProbeResult.NoAnswer,
            ProbeResult.Answered(40),
            ProbeResult.NoAnswer, ProbeResult.NoAnswer, ProbeResult.NoAnswer
        )

        assertEquals(LivenessAction.VPN_UP, failures[3])
        assertEquals(listOf(LivenessAction.NONE, LivenessAction.NONE, LivenessAction.NONE), failures.drop(4))
    }
}
