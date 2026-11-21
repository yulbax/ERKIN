package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.state.ToggleableState
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerFolder
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import io.github.yulbax.erkin.data.profile.ServerNode
import io.github.yulbax.erkin.data.profile.ServerTree
import io.github.yulbax.erkin.ui.components.Selection
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSelectionTest {
    private val saved = mutableStateOf(emptyList<Long>())
    private val riga = server(1, "Riga", SUBSCRIPTION)
    private val oslo = server(2, "Oslo", SUBSCRIPTION)
    private val tallinn = server(3, "Tallinn", SUBSCRIPTION, hidden = true)
    private val own = server(4, "Own")
    private val tree = ServerTree(
        listOf(riga, oslo, tallinn, own),
        mapOf(SUBSCRIPTION to ServerGroupEntity(id = SUBSCRIPTION, url = "https://sub.example/list"))
    )
    private val hidden = setOf(tallinn.id)

    @Test
    fun eachFolderOffersItsOwnActions() {
        assertEquals(listOf(BulkAction.MOVE, BulkAction.DELETE), FolderKind.ROOT.actionsFor(listOf(own.id), emptySet()))
        assertEquals(
            listOf(BulkAction.MOVE, BulkAction.REMOVE_FROM_GROUP, BulkAction.DELETE),
            FolderKind.LOCAL.actionsFor(listOf(own.id), emptySet())
        )
    }

    @Test
    fun aSubscriptionHidesVisibleServersShowsHiddenOnesAndNeverDeletes() {
        assertEquals(listOf(BulkAction.HIDE), FolderKind.SUBSCRIPTION.actionsFor(listOf(riga.id), hidden))
        assertEquals(listOf(BulkAction.SHOW), FolderKind.SUBSCRIPTION.actionsFor(listOf(tallinn.id), hidden))
        assertEquals(listOf(BulkAction.HIDE, BulkAction.SHOW), FolderKind.SUBSCRIPTION.actionsFor(listOf(riga.id, tallinn.id), hidden))
    }

    @Test
    fun hidingAndShowingTouchOnlyTheirSideOfAMixedSelection() {
        val checked = listOf(riga.id, tallinn.id, oslo.id)

        assertEquals(listOf(riga.id, oslo.id), BulkAction.HIDE.targets(checked, hidden))
        assertEquals(listOf(tallinn.id), BulkAction.SHOW.targets(checked, hidden))
        assertEquals(checked, BulkAction.DELETE.targets(checked, hidden))
    }

    @Test
    fun hidingIsOffOnlyWhenItWouldLeaveTheSubscriptionWithoutAVisibleServer() {
        val visible = setOf(riga.id, oslo.id)

        assertEquals(true, BulkAction.HIDE.keepsAServerVisible(listOf(riga.id), visible))
        assertEquals(false, BulkAction.HIDE.keepsAServerVisible(listOf(riga.id, oslo.id), visible))
        assertEquals(false, BulkAction.HIDE.keepsAServerVisible(listOf(riga.id, oslo.id, tallinn.id), visible))
        assertEquals(true, BulkAction.SHOW.keepsAServerVisible(listOf(tallinn.id), visible))
    }

    @Test
    fun theFolderKindFollowsTheGroupBehindIt() {
        val local = ServerTree(listOf(server(5, "A", LOCAL), server(6, "B", LOCAL)), mapOf(LOCAL to ServerGroupEntity(id = LOCAL, name = "Work")))

        assertEquals(FolderKind.ROOT, FolderKind.of(ServerFolder.Root, tree))
        assertEquals(FolderKind.SUBSCRIPTION, FolderKind.of(ServerFolder.Group(SUBSCRIPTION), tree))
        assertEquals(FolderKind.LOCAL, FolderKind.of(ServerFolder.Group(LOCAL), local))
    }

    @Test
    fun selectAllTakesTheVisibleServersAndTheHiddenOnesOnlyWhenTheirSectionIsOpen() {
        val nodes = tree.children(ServerFolder.Group(SUBSCRIPTION))
        val hiddenServers = tree.hidden(ServerFolder.Group(SUBSCRIPTION))

        assertEquals(listOf(riga.id, oslo.id), selectableIds(nodes, hiddenServers, hiddenShown = false))
        assertEquals(listOf(riga.id, oslo.id, tallinn.id), selectableIds(nodes, hiddenServers, hiddenShown = true))
    }

    @Test
    fun selectAllAtTheRootTakesTheVisibleMembersOfGroupsButNotTheirHiddenOnes() {
        assertEquals(listOf(riga.id, oslo.id, own.id), selectableIds(tree.root, emptyList(), hiddenShown = false))
    }

    @Test
    fun selectAllWhileSearchingTakesOnlyTheServersFound() {
        assertEquals(listOf(oslo.id), selectableIds(tree.search(ServerFolder.Root, "os"), emptyList(), hiddenShown = false))
    }

    @Test
    fun aGroupRowIsCheckedPartlyThenFullyThenClearedByItsToggle() {
        val group = tree.root.filterIsInstance<ServerNode.Group>().single()
        selection().toggle(riga.id)

        assertEquals(ToggleableState.Indeterminate, selection().stateOf(group.profileIds))
        selection().toggleAll(group.profileIds)
        assertEquals(ToggleableState.On, selection().stateOf(group.profileIds))
        selection().toggleAll(group.profileIds)
        assertEquals(ToggleableState.Off, selection().stateOf(group.profileIds))
    }

    @Test
    fun selectAllAddsToWhatIsAlreadyChecked() {
        selection().toggle(own.id)
        selection().select(listOf(riga.id, oslo.id))

        assertEquals(setOf(own.id, riga.id, oslo.id), selection().checked)
    }

    @Test
    fun serversThatNoLongerExistDropOutOfTheSelection() {
        selection().select(listOf(riga.id, own.id))

        assertEquals(setOf(riga.id), Selection(saved, listOf(riga.id, oslo.id)).checked)
    }

    @Test
    fun hidingClearsTheSelectionAtOnceWhileMovingWaitsForTheDialog() {
        val calls = mutableListOf<String>()
        var cleared = 0
        var finishMove: () -> Unit = {}
        val bulk = ServerBulkActions(
            onSetHidden = { ids, value -> calls += "hidden $ids $value" },
            onDelete = { ids -> calls += "delete $ids" },
            onRemoveFromGroup = { ids -> calls += "remove $ids" },
            onMove = { ids, done ->
                calls += "move $ids"
                finishMove = done
            },
            canMove = { true }
        )

        bulk.run(BulkAction.HIDE, listOf(riga.id), done = { cleared++ })
        assertEquals(1, cleared)
        bulk.run(BulkAction.MOVE, listOf(own.id), done = { cleared++ })
        assertEquals(1, cleared)
        finishMove()
        assertEquals(2, cleared)
        bulk.run(BulkAction.DELETE, listOf(own.id), done = { cleared++ })
        assertEquals(2, cleared)
        assertEquals(listOf("hidden [1] true", "move [4]", "delete [4]"), calls)
    }


    private fun selection() = Selection(saved, tree.profiles.map { it.id })

    private fun server(id: Long, name: String, groupId: Long? = null, hidden: Boolean = false) =
        ProfileEntity(id = id, name = name, type = "vless", link = "vless://$id", outboundJson = "{}", groupId = groupId, hidden = hidden)

    private companion object {
        const val SUBSCRIPTION = 10L
        const val LOCAL = 11L
    }
}
