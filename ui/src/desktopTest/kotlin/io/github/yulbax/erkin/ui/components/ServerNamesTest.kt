package io.github.yulbax.erkin.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerNamesTest {

    @Test
    fun aServerWithoutANameIsShownAsUnnamed() {
        val names = ServerNames("Unnamed")

        assertEquals("Unnamed", names.of(""))
        assertEquals("Amsterdam", names.of("Amsterdam"))
    }
}
