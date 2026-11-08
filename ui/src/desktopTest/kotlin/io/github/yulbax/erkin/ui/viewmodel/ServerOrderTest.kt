package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.vpn.core.ProxyDelay
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerOrderTest {

    private val profiles = listOf(
        ProfileEntity(id = 1, name = "own slow"),
        ProfileEntity(id = 2, name = "sub dead", groupId = 7),
        ProfileEntity(id = 3, name = "own fast"),
        ProfileEntity(id = 4, name = "sub fast", groupId = 7)
    )
    private val delays = mapOf(
        1L to ProxyDelay.Measured(300),
        2L to ProxyDelay.Failed,
        3L to ProxyDelay.Measured(40),
        4L to ProxyDelay.Measured(90)
    )

    @Test
    fun sortingEveryServerOrdersTheWholeList() {
        assertEquals(listOf(3L, 4L, 1L, 2L), profiles.sortedByDelay(setOf(1L, 2L, 3L, 4L), delays).map { it.id })
    }

    @Test
    fun sortingSomeServersLeavesTheOthersInPlace() {
        assertEquals(listOf(1L, 4L, 3L, 2L), profiles.sortedByDelay(setOf(2L, 4L), delays).map { it.id })
    }
}
