package io.github.yulbax.erkin.ui.screens.erkin

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerMenuEndTest {
    private val subscription = ServerGroupEntity(id = 1, url = "https://sub.example/list")
    private val local = ServerGroupEntity(id = 2, name = "Work")

    @Test
    fun aSubscriptionOfOneServerIsDeletedRatherThanHidden() {
        val only = server(1, groupId = 1)

        assertEquals(ServerMenuEnd.DELETE, ServerMenuEnd.of(only, subscription, listOf(only)))
    }

    @Test
    fun serversOfALargerSubscriptionAreHiddenAndHiddenOnesShown() {
        val riga = server(1, groupId = 1)
        val oslo = server(2, groupId = 1, hidden = true)

        assertEquals(ServerMenuEnd.HIDE, ServerMenuEnd.of(riga, subscription, listOf(riga, oslo)))
        assertEquals(ServerMenuEnd.SHOW, ServerMenuEnd.of(oslo, subscription, listOf(riga, oslo)))
    }

    @Test
    fun ownServersAndLocalGroupMembersAreDeleted() {
        val own = server(1)
        val member = server(2, groupId = 2)

        assertEquals(ServerMenuEnd.DELETE, ServerMenuEnd.of(own, null, emptyList()))
        assertEquals(ServerMenuEnd.DELETE, ServerMenuEnd.of(member, local, listOf(member, server(3, groupId = 2))))
    }

    @Test
    fun theLastVisibleServerOfASubscriptionCannotBeHidden() {
        val riga = server(1, groupId = 1)
        val oslo = server(2, groupId = 1)
        val tallinn = server(3, groupId = 1, hidden = true)

        assertEquals(true, ServerMenuEnd.HIDE.allowedFor(riga, listOf(riga, oslo, tallinn)))
        assertEquals(false, ServerMenuEnd.HIDE.allowedFor(riga, listOf(riga, oslo.copy(hidden = true), tallinn)))
    }

    private fun server(id: Long, groupId: Long? = null, hidden: Boolean = false) =
        ProfileEntity(id = id, name = "s$id", type = "vless", link = "vless://$id", groupId = groupId, hidden = hidden)
}
