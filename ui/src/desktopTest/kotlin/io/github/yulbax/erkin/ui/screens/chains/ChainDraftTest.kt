package io.github.yulbax.erkin.ui.screens.chains

import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProxyChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainDraftTest {

    private val a = ChainEntry.Server(1)
    private val b = ChainEntry.Server(2)
    private val c = ChainEntry.Server(3)

    @Test
    fun movingTheLastServerUpMakesTheOneBeforeItTheExit() {
        val draft = ChainDraft(listOf(a, b, c)).move(from = 2, to = 0)

        assertEquals(ProxyChain(listOf(c, a), 2), draft.chain)
    }

    @Test
    fun byeDpiStaysTheEntryWhenServersAreMoved() {
        val draft = ChainDraft(listOf(ChainEntry.ByeDpi, a, b))

        assertEquals(draft, draft.move(from = 0, to = 2))
        assertEquals(ProxyChain(listOf(ChainEntry.ByeDpi, b), 1), draft.move(from = 2, to = 0).chain)
        assertTrue(draft.isPinned(0))
    }

    @Test
    fun aServerAloneAfterByeDpiHasNowhereToMove() {
        val alone = ChainDraft(listOf(ChainEntry.ByeDpi, a))

        assertFalse(alone.isMovable(0))
        assertFalse(alone.isMovable(1))
        assertTrue(alone.add(2).isMovable(1))
        assertTrue(ChainDraft(listOf(a, b)).isMovable(0))
    }

    @Test
    fun choosingAServerThatIsAlreadyInTheChainSwapsTheTwoNodes() {
        assertEquals(ProxyChain(listOf(b, a), 3), ChainDraft(listOf(a, b, c)).select(0, b).chain)
    }

    @Test
    fun choosingAServerForByeDpiDropsByeDpiInsteadOfMovingIt() {
        val draft = ChainDraft(listOf(ChainEntry.ByeDpi, a, b)).select(0, b)

        assertEquals(listOf(b, a), draft.nodes)
        assertTrue(draft.chain!!.isValid)
    }

    @Test
    fun onlyHopsBetweenTheEntryAndTheExitCanBeRemovedAndNewOnesGoBeforeTheExit() {
        val draft = ChainDraft(listOf(a, b))

        assertFalse(draft.isRemovable(0))
        assertFalse(draft.isRemovable(1))
        assertEquals(listOf(a, c, b), draft.add(3).nodes)
        assertEquals(listOf(a, b), draft.add(3).remove(1).nodes)
    }

    @Test
    fun aNewChainStartsWithByeDpiAndTheFirstServer() {
        assertEquals(ProxyChain(listOf(ChainEntry.ByeDpi), 1), ChainDraft.of(null, 1).chain)
        assertEquals(ProxyChain(listOf(a), 2), ChainDraft.of(ProxyChain(listOf(a), 2), 9).chain)
    }
}
