package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerListItemsTest {
    private val manual = server(1, "Manual")
    private val single = server(2, "Single", SINGLE_ID)
    private val germany = server(3, "Germany", GROUP_ID)
    private val manualAfter = server(4, "After")
    private val france = server(5, "France", GROUP_ID)
    private val stored = mapOf(
        SINGLE_ID to SubscriptionEntity(id = SINGLE_ID, url = "https://one.example/list"),
        GROUP_ID to SubscriptionEntity(id = GROUP_ID, url = GROUP_URL)
    )

    @Test
    fun groupsASubscriptionAtItsFirstServerAndLeavesASingleServerSubscriptionInline() {
        val items = serverListItems(listOf(manual, single, germany, manualAfter, france), selectedId = null, stored)

        assertEquals(
            listOf(
                ServerListItem.Server(manual),
                ServerListItem.Server(single, subscriptionId = SINGLE_ID),
                ServerListItem.Subscription(GROUP_ID, GROUP_URL, "sub.example", null, listOf(germany, france), current = germany),
                ServerListItem.Server(manualAfter)
            ),
            items
        )
    }

    @Test
    fun theCurrentServerIsTheSelectedOneThenTheRememberedOne() {
        val profiles = listOf(germany, france, manual)
        val remembered = mapOf(GROUP_ID to SubscriptionEntity(id = GROUP_ID, url = GROUP_URL, name = "Mine", currentId = france.id))

        assertEquals(france, subscriptionOf(serverListItems(profiles, selectedId = france.id, stored)).current)
        assertEquals(france, subscriptionOf(serverListItems(profiles, selectedId = manual.id, remembered)).current)
        assertEquals(germany, subscriptionOf(serverListItems(profiles, selectedId = manual.id, stored)).current)
        assertEquals("Mine", subscriptionOf(serverListItems(profiles, selectedId = null, remembered)).name)
    }

    private fun subscriptionOf(items: List<ServerListItem>) = items.filterIsInstance<ServerListItem.Subscription>().single()

    private fun server(id: Long, name: String, subscriptionId: Long? = null) =
        ProfileEntity(id = id, name = name, type = "vless", link = "vless://$id", outboundJson = "{}", subscriptionId = subscriptionId)

    private companion object {
        const val GROUP_URL = "https://sub.example/list"
        const val GROUP_ID = 10L
        const val SINGLE_ID = 11L
    }
}
