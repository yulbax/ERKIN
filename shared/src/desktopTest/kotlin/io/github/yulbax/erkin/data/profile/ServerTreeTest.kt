package io.github.yulbax.erkin.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTreeTest {
    private val manual = server(1, "Manual")
    private val single = server(2, "Single", SINGLE_ID)
    private val germany = server(3, "Germany", GROUP_ID)
    private val manualAfter = server(4, "After")
    private val france = server(5, "France", GROUP_ID)
    private val stored = mapOf(
        SINGLE_ID to ServerGroupEntity(id = SINGLE_ID, url = "https://one.example/list"),
        GROUP_ID to ServerGroupEntity(id = GROUP_ID, url = GROUP_URL)
    )

    @Test
    fun groupsASubscriptionAtItsFirstServerAndLeavesASingleServerSubscriptionInline() {
        val items = ServerTree(listOf(manual, single, germany, manualAfter, france), stored).root

        assertEquals(
            listOf(
                ServerNode.Server(manual),
                ServerNode.Server(single, stored.getValue(SINGLE_ID)),
                ServerNode.Group(GROUP_ID, GroupKind.Subscription(GROUP_URL), "sub.example", null, listOf(germany, france), rememberedId = null, notes = SubscriptionNotes.NONE),
                ServerNode.Server(manualAfter)
            ),
            items
        )
    }

    @Test
    fun theCurrentServerIsTheRememberedOneThenTheFirst() {
        val profiles = listOf(germany, france, manual)
        val remembered = mapOf(GROUP_ID to ServerGroupEntity(id = GROUP_ID, url = GROUP_URL, name = "Mine", currentId = france.id))

        assertEquals(france, subscriptionOf(ServerTree(profiles, remembered).root).current)
        assertEquals(germany, subscriptionOf(ServerTree(profiles, stored).root).current)
        assertEquals("Mine", subscriptionOf(ServerTree(profiles, remembered).root).name)
    }

    @Test
    fun aLocalGroupStaysAGroupEvenWithOneServer() {
        val lone = server(6, "Lone", LOCAL_ID)
        val groups = mapOf(LOCAL_ID to ServerGroupEntity(id = LOCAL_ID, name = "Work"))

        assertEquals(
            listOf(ServerNode.Group(LOCAL_ID, GroupKind.Local, "Work", null, listOf(lone), rememberedId = null)),
            ServerTree(listOf(lone), groups).root
        )
    }

    @Test
    fun onlyARefreshedSubscriptionCarriesItsUpdateTime() {
        val lone = server(6, "Lone", LOCAL_ID)
        val profiles = listOf(germany, france, lone)
        val groups = mapOf(
            GROUP_ID to ServerGroupEntity(id = GROUP_ID, url = GROUP_URL, updatedAt = UPDATED_AT),
            LOCAL_ID to ServerGroupEntity(id = LOCAL_ID, name = "Work", updatedAt = UPDATED_AT)
        )
        val items = ServerTree(profiles, groups).root.filterIsInstance<ServerNode.Group>()

        assertEquals(listOf(UPDATED_AT, null), items.map { it.updatedAt })
        assertEquals(null, subscriptionOf(ServerTree(listOf(germany, france), stored).root).updatedAt)
    }

    private fun subscriptionOf(items: List<ServerNode>) = items.filterIsInstance<ServerNode.Group>().single()

    private fun server(id: Long, name: String, groupId: Long? = null) =
        ProfileEntity(id = id, name = name, type = "vless", link = "vless://$id", outboundJson = "{}", groupId = groupId)

    private companion object {
        const val GROUP_URL = "https://sub.example/list"
        const val GROUP_ID = 10L
        const val SINGLE_ID = 11L
        const val LOCAL_ID = 12L
        const val UPDATED_AT = 1_762_000_000_000L
    }
}
