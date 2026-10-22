package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerListItemsTest {
    private val manual = server(1, "Manual")
    private val single = server(2, "Single", SINGLE_URL)
    private val germany = server(3, "Germany", GROUP_URL)
    private val manualAfter = server(4, "After")
    private val france = server(5, "France", GROUP_URL)

    @Test
    fun groupsASubscriptionAtItsFirstServerAndLeavesASingleServerSubscriptionInline() {
        val items = serverListItems(listOf(manual, single, germany, manualAfter, france), selectedId = null, emptyMap())

        assertEquals(
            listOf(
                ServerListItem.Server(manual),
                ServerListItem.Server(single, subscriptionUrl = SINGLE_URL),
                ServerListItem.Subscription(GROUP_URL, "sub.example", null, listOf(germany, france), current = germany),
                ServerListItem.Server(manualAfter)
            ),
            items
        )
    }

    @Test
    fun theCurrentServerIsTheSelectedOneThenTheRememberedOne() {
        val profiles = listOf(germany, france, manual)
        val remembered = mapOf(GROUP_URL to SubscriptionEntity(GROUP_URL, name = "Mine", currentId = france.id))

        assertEquals(france, subscriptionOf(serverListItems(profiles, selectedId = france.id, emptyMap())).current)
        assertEquals(france, subscriptionOf(serverListItems(profiles, selectedId = manual.id, remembered)).current)
        assertEquals(germany, subscriptionOf(serverListItems(profiles, selectedId = manual.id, emptyMap())).current)
        assertEquals("Mine", subscriptionOf(serverListItems(profiles, selectedId = null, remembered)).name)
    }

    private fun subscriptionOf(items: List<ServerListItem>) = items.filterIsInstance<ServerListItem.Subscription>().single()

    private fun server(id: Long, name: String, url: String = "") =
        ProfileEntity(id = id, name = name, type = "vless", link = "vless://$id", outboundJson = "{}", subscriptionUrl = url)

    private companion object {
        const val GROUP_URL = "https://sub.example/list"
        const val SINGLE_URL = "https://one.example/list"
    }
}
