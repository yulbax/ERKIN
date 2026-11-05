package io.github.yulbax.erkin.ui.screens.chains

import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProxyChain

internal data class ChainDraft(val nodes: List<ChainEntry>) {

    val chain: ProxyChain?
        get() {
            val exit = nodes.lastOrNull() as? ChainEntry.Server ?: return null
            return ProxyChain(nodes.dropLast(1), exit.profileId)
        }

    val lastIndex: Int get() = nodes.lastIndex

    fun isPinned(index: Int): Boolean = nodes[index] is ChainEntry.ByeDpi

    fun isMovable(index: Int): Boolean = !isPinned(index) && nodes.count { it !is ChainEntry.ByeDpi } > 1

    fun isRemovable(index: Int): Boolean = index in 1 until lastIndex

    fun options(index: Int, servers: List<Long>): List<ChainEntry> =
        listOfNotNull(ChainEntry.ByeDpi.takeIf { index == 0 }) + servers.map(ChainEntry::Server)

    fun select(index: Int, entry: ChainEntry): ChainDraft {
        val other = nodes.indexOf(entry).takeIf { it >= 0 && it != index }
        val next = nodes.toMutableList()
        if (other != null) next[other] = nodes[index]
        next[index] = entry
        return ChainDraft(next.filterIndexed { i, node -> i == 0 || node !is ChainEntry.ByeDpi })
    }

    fun move(from: Int, to: Int): ChainDraft {
        val first = if (isPinned(0)) 1 else 0
        if (from < first) return this
        val target = to.coerceIn(first, lastIndex)
        return ChainDraft(nodes.toMutableList().apply { add(target, removeAt(from)) })
    }

    fun remove(index: Int): ChainDraft = if (isRemovable(index)) ChainDraft(nodes.filterIndexed { i, _ -> i != index }) else this

    fun add(serverId: Long): ChainDraft = ChainDraft(nodes.toMutableList().apply { add(lastIndex, ChainEntry.Server(serverId)) })

    fun uses(serverId: Long): Boolean = ChainEntry.Server(serverId) in nodes

    companion object {
        fun of(chain: ProxyChain?, firstServer: Long?): ChainDraft = when {
            chain != null -> ChainDraft(chain.hops + ChainEntry.Server(chain.exitId))
            else -> ChainDraft(listOfNotNull(ChainEntry.ByeDpi, firstServer?.let(ChainEntry::Server)))
        }
    }
}
