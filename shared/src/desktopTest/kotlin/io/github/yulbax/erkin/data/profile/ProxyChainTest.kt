package io.github.yulbax.erkin.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyChainTest {
    @Test
    fun longChainsSurviveEncoding() {
        val chain = ProxyChain(listOf(ChainEntry.ByeDpi, ChainEntry.Server(3), ChainEntry.Server(5)), 7)
        assertEquals(chain, ProxyChain.decode(chain.encode()))
        assertEquals(listOf(3L, 5L, 7L), chain.serverIds)
    }

    @Test
    fun chainsSavedBeforeHopsStillDecode() {
        assertEquals(ProxyChain(ChainEntry.Server(3), 4), ProxyChain.decode("""{"entry":3,"exit":4}"""))
        assertEquals(ProxyChain(ChainEntry.ByeDpi, 4), ProxyChain.decode("""{"entry":"byedpi","exit":4}"""))
    }

    @Test
    fun byeDpiOnlyLeadsAndServersAppearOnce() {
        assertTrue(ProxyChain(listOf(ChainEntry.Server(1), ChainEntry.Server(2)), 3).isValid)
        assertFalse(ProxyChain(listOf(ChainEntry.Server(1), ChainEntry.ByeDpi), 3).isValid)
        assertFalse(ProxyChain(listOf(ChainEntry.Server(1), ChainEntry.Server(3)), 3).isValid)
        assertFalse(ProxyChain(emptyList(), 3).isValid)
    }
}
