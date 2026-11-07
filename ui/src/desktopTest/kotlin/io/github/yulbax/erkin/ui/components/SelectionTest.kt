package io.github.yulbax.erkin.ui.components

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    @Test
    fun itemsAreCheckedOneByOneOrAsAGroupAndGoneItemsDropOut() {
        val saved = mutableStateOf(listOf(9L))
        fun selection() = Selection(saved, listOf(1L, 2L, 3L))

        assertFalse(selection().active)
        selection().toggle(1L)
        selection().toggleAll(listOf(2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), selection().checked)
        selection().toggleAll(listOf(2L, 3L))
        assertEquals(setOf(1L), selection().checked)
        selection().selectAll()
        selection().forget(listOf(2L))
        assertEquals(setOf(1L, 3L), selection().checked)
        assertTrue(3L in selection())
        selection().clear()
        assertFalse(selection().active)
    }
}
