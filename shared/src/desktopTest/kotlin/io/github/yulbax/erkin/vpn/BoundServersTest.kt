package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundServersTest {

    private val missed = mapOf(2L to ProbeResult.NoAnswer)
    private val answered = mapOf(2L to ProbeResult.Answered(40))

    @Test
    fun twoMissesInARowMakeAServerUnreachable() {
        val misses = BoundMisses()

        misses.record(missed)
        assertEquals(emptySet<Long>(), misses.unreachable)
        misses.record(missed)
        assertEquals(setOf(2L), misses.unreachable)
    }

    @Test
    fun anAnswerClearsTheMisses() {
        val misses = BoundMisses()

        misses.record(missed)
        misses.record(missed)
        misses.record(answered)
        assertEquals(emptySet<Long>(), misses.unreachable)
        misses.record(missed)
        assertEquals(emptySet<Long>(), misses.unreachable)
    }

    @Test
    fun aServerIsRetriedQuicklyOnlyUntilItIsUnreachable() {
        val misses = BoundMisses()

        misses.record(missed)
        assertTrue(misses.shouldHurry(missed))
        misses.record(missed)
        assertFalse(misses.shouldHurry(missed))
        assertFalse(misses.shouldHurry(answered))
    }
}
