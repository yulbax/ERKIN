package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.vpn.core.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDelayOrderTest {

    private val profiles = listOf(
        ProfileEntity(id = 1, name = "own slow"),
        ProfileEntity(id = 2, name = "sub dead", groupId = GROUP_ID),
        ProfileEntity(id = 3, name = "own fast"),
        ProfileEntity(id = 4, name = "sub fast", groupId = GROUP_ID)
    )
    private val results = mapOf(
        1L to ProbeResult.Answered(300),
        2L to ProbeResult.NoAnswer,
        3L to ProbeResult.Answered(40),
        4L to ProbeResult.Answered(90)
    )
    private val everything = setOf(1L, 2L, 3L, 4L)

    @Test
    fun aGroupThatRemembersItsFastestServerStandsWhereThatServerDoes() {
        assertEquals(listOf(3L, 4L, 2L, 1L), ServerTree(profiles, groups(currentId = 4)).orderedByDelay(everything, results))
    }

    @Test
    fun aGroupStandsWhereTheServerItShowsDoesNotWhereItsFastestDoes() {
        assertEquals(listOf(3L, 1L, 4L, 2L), ServerTree(profiles, groups(currentId = 2)).orderedByDelay(everything, results))
    }

    @Test
    fun testingOneGroupOnlySortsItsServersAndLeavesTheListInPlace() {
        assertEquals(listOf(1L, 4L, 2L, 3L), ServerTree(profiles, groups(currentId = 2)).orderedByDelay(setOf(2L, 4L), results))
    }

    @Test
    fun hiddenServersKeepTheirPlaceAfterTheVisibleOnesOfTheirGroup() {
        val withHidden = profiles + ProfileEntity(id = 5, name = "sub hidden", groupId = GROUP_ID, hidden = true)

        assertEquals(listOf(3L, 4L, 2L, 5L, 1L), ServerTree(withHidden, groups(currentId = 4)).orderedByDelay(everything, results))
    }

    @Test
    fun theServersTheListShowsAreTestedBeforeTheOnesInsideGroups() {
        assertEquals(listOf(1L, 4L, 3L, 2L), ServerTree(profiles, groups(currentId = 4)).testQueue())
    }

    private fun groups(currentId: Long) =
        mapOf(GROUP_ID to ServerGroupEntity(id = GROUP_ID, url = "https://sub.example/list", currentId = currentId))

    private companion object {
        const val GROUP_ID = 7L
    }
}
