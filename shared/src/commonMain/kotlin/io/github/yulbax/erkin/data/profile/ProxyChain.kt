package io.github.yulbax.erkin.data.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ChainEntry {
    data object ByeDpi : ChainEntry
    data class Server(val profileId: Long) : ChainEntry
}

data class ProxyChain(val hops: List<ChainEntry>, val exitId: Long) {

    constructor(entry: ChainEntry, exitId: Long) : this(listOf(entry), exitId)

    val serverIds: List<Long> get() = hops.mapNotNull { (it as? ChainEntry.Server)?.profileId } + exitId

    val isValid: Boolean
        get() = hops.isNotEmpty() && hops.drop(1).none { it is ChainEntry.ByeDpi } && serverIds.size == serverIds.toSet().size

    fun uses(profileId: Long): Boolean = profileId in serverIds

    fun remap(ids: Map<Long, Long>): ProxyChain? {
        val remapped = hops.map { hop ->
            when (hop) {
                ChainEntry.ByeDpi -> hop
                is ChainEntry.Server -> ChainEntry.Server(ids[hop.profileId] ?: return null)
            }
        }
        return ProxyChain(remapped, ids[exitId] ?: return null)
    }

    fun encode(): String = JsonObject(
        mapOf(
            HOPS to JsonArray(hops.map { hop ->
                when (hop) {
                    ChainEntry.ByeDpi -> JsonPrimitive(BYEDPI)
                    is ChainEntry.Server -> JsonPrimitive(hop.profileId)
                }
            }),
            EXIT to JsonPrimitive(exitId)
        )
    ).toString()

    companion object {
        const val TYPE = "chain"
        private const val HOPS = "hops"
        private const val LEGACY_ENTRY = "entry"
        private const val EXIT = "exit"
        private const val BYEDPI = "byedpi"

        fun decode(json: String): ProxyChain? = runCatching {
            val obj = Json.parseToJsonElement(json) as JsonObject
            val exit = obj.getValue(EXIT).jsonPrimitive.longOrNull ?: return null
            val hops = obj[HOPS]?.jsonArray ?: JsonArray(listOf(obj.getValue(LEGACY_ENTRY)))
            ProxyChain(hops.map { hopOf(it) ?: return null }, exit)
        }.getOrNull()

        private fun hopOf(element: JsonElement): ChainEntry? {
            val value = element.jsonPrimitive
            return if (value.contentOrNull == BYEDPI) ChainEntry.ByeDpi else value.longOrNull?.let(ChainEntry::Server)
        }
    }
}
