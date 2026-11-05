package io.github.yulbax.erkin.ui.viewmodel

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsStateTest {
    private val telegram = AppInfo("org.telegram", "Telegram", isSystemApp = false, connectionType = ConnectionType.VPN)
    private val discord = AppInfo("com.discord", "Discord", isSystemApp = false, connectionType = ConnectionType.BYEDPI)
    private val main = ProfileEntity(id = 1, name = "Main", selected = true)
    private val helsinki = ProfileEntity(id = 2, name = "Helsinki")
    private val riga = ProfileEntity(id = 3, name = "Riga", subscriptionId = SUBSCRIPTION)
    private val oslo = ProfileEntity(id = 4, name = "Oslo", subscriptionId = SUBSCRIPTION)
    private val chain = ProfileEntity(
        id = 5,
        name = "ByeDPI → Helsinki",
        type = ProxyChain.TYPE,
        outboundJson = ProxyChain(ChainEntry.ByeDpi, helsinki.id).encode()
    )
    private val choices = ServerChoices(
        listOf(main, helsinki, riga, oslo, chain),
        mapOf(SUBSCRIPTION to SubscriptionEntity(id = SUBSCRIPTION, url = "https://sub.example/list"))
    )

    @Test
    fun filtersByChannelAndQueryTogether() {
        val apps = listOf(telegram, discord)

        assertEquals(apps, filterApps(apps, query = " ", filter = null))
        assertEquals(listOf(discord), filterApps(apps, query = "", filter = AppFilter.Channel(ConnectionType.BYEDPI)))
        assertEquals(listOf(telegram), filterApps(apps, query = "org.tele", filter = null))
        assertEquals(emptyList<AppInfo>(), filterApps(apps, query = "telegram", filter = AppFilter.Channel(ConnectionType.BYEDPI)))
    }

    @Test
    fun selectionTogglesPackagesAndDrivesRowModes() {
        val selecting = AppsMode.Selecting().toggled(telegram.packageName)

        assertEquals(AppRowMode.Selecting(checked = true), selecting.rowMode(telegram.packageName))
        assertEquals(AppRowMode.Selecting(checked = false), selecting.rowMode(discord.packageName))
        assertEquals(emptySet<String>(), selecting.toggled(telegram.packageName).packages)
        assertEquals(AppRowMode.Browsing, AppsMode.Browsing.rowMode(telegram.packageName))
    }

    @Test
    fun blockIsAllowedUnlessEveryAppIsSelected() {
        val selecting = AppsMode.Selecting(setOf(telegram.packageName))

        assertTrue(selecting.canBlock(totalApps = 2))
        assertFalse(selecting.canBlock(totalApps = 1))
    }

    @Test
    fun aServerFilterShowsOnlyTheAppsBoundToThatServer() {
        val bound = telegram.copy(packageName = "org.bound", serverId = helsinki.id)
        val alsoBound = telegram.copy(packageName = "org.also", serverId = helsinki.id)
        val onChain = telegram.copy(packageName = "org.chain", serverId = chain.id)
        val apps = listOf(telegram, discord, bound, alsoBound, onChain, discord.copy(packageName = "org.dpi", serverId = helsinki.id))

        assertEquals(listOf(bound, alsoBound), filterApps(apps, query = "", filter = AppFilter.Server(helsinki.id)))
        assertEquals(listOf(helsinki.id to 2, chain.id to 1), choices.boundServers(apps))
    }

    @Test
    fun anAppFollowingTheMainServerHasNoCustomLabel() {
        assertEquals(CurrentServer.Main, choices.current(telegram))
        assertNull(choices.labelFor(telegram))
    }

    @Test
    fun anAppBoundToTheMainServerOnPurposeShowsItsName() {
        assertEquals(CurrentServer.Bound(main.id), choices.current(telegram.copy(serverId = main.id)))
        assertEquals(main.name, choices.labelFor(telegram.copy(serverId = main.id))?.name)
    }

    @Test
    fun aChannelOtherThanVpnHasNoServer() {
        assertEquals(CurrentServer.None, choices.current(discord.copy(serverId = helsinki.id)))
        assertNull(choices.labelFor(discord.copy(serverId = helsinki.id)))
    }

    @Test
    fun aBoundServerShowsItsNameAndAChainShowsItsLastNode() {
        assertEquals(ServerLabel("Helsinki", isChain = false), choices.labelFor(telegram.copy(serverId = helsinki.id)))
        assertEquals(ServerLabel("Helsinki", isChain = true), choices.labelFor(telegram.copy(serverId = chain.id)))
    }

    @Test
    fun aRuleUsesTheSameServerLabelsAsAnApp() {
        assertEquals(ServerLabel("Helsinki", isChain = true), choices.label(ConnectionType.VPN, chain.id))
        assertNull(choices.label(ConnectionType.VPN, serverId = 99))
        assertNull(choices.label(ConnectionType.DIRECT, helsinki.id))
        assertEquals(CurrentServer.Bound(helsinki.id), choices.current(ConnectionType.VPN, helsinki.id))
    }

    @Test
    fun aServerThatDoesNotAnswerIsFlaggedOnItsLabel() {
        val down = ServerChoices(choices.servers, choices.subscriptions, unreachable = setOf(helsinki.id))

        assertEquals(ServerLabel("Helsinki", isChain = false, unreachable = true), down.labelFor(telegram.copy(serverId = helsinki.id)))
        assertEquals(ServerLabel("Riga", isChain = false), down.labelFor(telegram.copy(serverId = riga.id)))
    }

    @Test
    fun serversCanBeChosenOnlyWhenThereIsMoreThanOne() {
        assertTrue(choices.canChoose)
        assertFalse(ServerChoices(listOf(main)).canChoose)
        assertFalse(ServerChoices().vpnEnabled)
    }

    @Test
    fun rowsCollapseASubscriptionAndMarkTheMainServer() {
        val rows = serverPickerRows(choices, query = "", expanded = emptySet(), current = CurrentServer.Main)

        assertEquals(
            listOf(
                ServerPickerRow.Main("Main", checked = true),
                ServerPickerRow.Server(1, "Main", checked = false),
                ServerPickerRow.Server(2, "Helsinki", checked = false),
                ServerPickerRow.Subscription(SUBSCRIPTION, "sub.example", count = 2, open = false),
                ServerPickerRow.Server(5, "ByeDPI → Helsinki", checked = false)
            ),
            rows
        )
    }

    @Test
    fun rowsOpenASubscriptionAndMarkTheBoundServer() {
        val rows = serverPickerRows(choices, query = "", expanded = setOf(SUBSCRIPTION), current = CurrentServer.Bound(oslo.id))

        assertEquals(ServerPickerRow.Server(3, "Riga", checked = false, indented = true), rows[4])
        assertEquals(ServerPickerRow.Server(4, "Oslo", checked = true, indented = true), rows[5])
        assertEquals(ServerPickerRow.Main("Main", checked = false), rows[0])
    }

    @Test
    fun searchListsMatchingServersFlatAcrossSubscriptions() {
        val rows = serverPickerRows(choices, query = " os", expanded = emptySet(), current = CurrentServer.None)

        assertEquals(listOf(ServerPickerRow.Server(4, "Oslo", checked = false)), rows)
    }

    private companion object {
        const val SUBSCRIPTION = 7L
    }
}
