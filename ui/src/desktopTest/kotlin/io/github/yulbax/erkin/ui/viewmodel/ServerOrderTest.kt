package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerOrderTest {

    private val profiles = listOf(
        ProfileEntity(id = 1, name = "own slow"),
        ProfileEntity(id = 2, name = "sub dead", groupId = GROUP_ID),
        ProfileEntity(id = 3, name = "own fast"),
        ProfileEntity(id = 4, name = "sub fast", groupId = GROUP_ID)
    )
    private val delays = mapOf(
        1L to ProxyDelay.Measured(300),
        2L to ProxyDelay.Failed,
        3L to ProxyDelay.Measured(40),
        4L to ProxyDelay.Measured(90)
    )
    private val everything = setOf(1L, 2L, 3L, 4L)

    @Test
    fun aGroupThatRemembersItsFastestServerStandsWhereThatServerDoes() {
        assertEquals(listOf(3L, 4L, 2L, 1L), profiles.orderByDelay(everything, delays, groups(currentId = 4)))
    }

    @Test
    fun aGroupStandsWhereTheServerItShowsDoesNotWhereItsFastestDoes() {
        assertEquals(listOf(3L, 1L, 4L, 2L), profiles.orderByDelay(everything, delays, groups(currentId = 2)))
    }

    @Test
    fun testingOneGroupOnlySortsItsServersAndLeavesTheListInPlace() {
        assertEquals(listOf(1L, 4L, 2L, 3L), profiles.orderByDelay(setOf(2L, 4L), delays, groups(currentId = 2)))
    }

    private fun groups(currentId: Long) =
        mapOf(GROUP_ID to ServerGroupEntity(id = GROUP_ID, url = "https://sub.example/list", currentId = currentId))

    private companion object {
        const val GROUP_ID = 7L
    }
}
