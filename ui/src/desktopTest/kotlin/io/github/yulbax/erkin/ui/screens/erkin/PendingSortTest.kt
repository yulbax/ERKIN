package io.github.yulbax.erkin.ui.screens.erkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSortTest {

    @Test
    fun theSortIsShownOnceTheListTakesItsOrder() {
        val sort = PendingSort(order = listOf(3L, 1L, 2L), shownBefore = listOf(1L, 2L, 3L))

        assertFalse(sort.isShownIn(listOf(1L, 2L, 3L)))
        assertFalse(sort.isOutdatedBy(listOf(1L, 2L, 3L)))
        assertTrue(sort.isShownIn(listOf(3L, 1L, 2L)))
        assertTrue(sort.isShownIn(listOf(1L, 2L)))
    }

    @Test
    fun anotherChangeOfTheListDropsTheSort() {
        val sort = PendingSort(order = listOf(3L, 1L, 2L), shownBefore = listOf(1L, 2L, 3L))

        assertTrue(sort.isOutdatedBy(listOf(1L, 3L, 2L)))
        assertTrue(sort.isOutdatedBy(listOf(1L, 2L, 3L, 4L)))
    }
}
