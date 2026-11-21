package io.github.yulbax.erkin.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSortTest {
    @Test
    fun namesCompareNumbersAsNumbersAndSkipLeadingFlags() {
        val names = listOf("United States — #108", "🇺🇸 United States — #86", "albania", "United States — #9")

        assertEquals(
            listOf("albania", "United States — #9", "🇺🇸 United States — #86", "United States — #108"),
            names.sortedWith(ServerNameOrder)
        )
    }

    @Test
    fun groupsSortByTheirNameUnlessTheyShowAsTheirOnlyServer() {
        val groups = mapOf(
            1L to ServerGroupEntity(id = 1, name = "Zeta"),
            2L to ServerGroupEntity(id = 2, url = "https://sub.example/link", name = "Omega")
        )
        val servers = listOf(
            ProfileEntity(id = 10, name = "Alpha", groupId = 1),
            ProfileEntity(id = 20, name = "Beta"),
            ProfileEntity(id = 30, name = "Aardvark", groupId = 2)
        )

        assertEquals(listOf(30L, 20L, 10L), ServerTree(servers, groups).orderedByName())
    }
}
