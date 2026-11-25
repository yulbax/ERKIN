package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.CoreSchema
import java.math.BigInteger

internal object ClashYamlReader : FormatReader {
    override fun detects(text: String): Boolean = proxyObjects(text) != null

    override fun read(text: String): List<ParsedProfile> =
        proxyObjects(text).orEmpty().mapNotNull { proxy ->
            val name = (proxy["name"] as? JsonPrimitive)?.contentOrNull
                ?: (proxy["server"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            LinkParser.parse(CoreProxySource.encode(name, proxy))
        }

    private fun proxyObjects(text: String): List<JsonObject>? {
        val root = ClashYamlDocument.load(text) ?: return null
        val items = when (root) {
            is JsonObject -> (root["proxies"] as? JsonArray)?.toList()
            is JsonArray -> root.toList().takeIf { list -> list.isNotEmpty() && list.all(::isProxyFragment) }
            else -> null
        } ?: return null
        return items.filterIsInstance<JsonObject>()
    }

    private fun isProxyFragment(element: JsonElement): Boolean =
        element is JsonObject && "type" in element && "server" in element
}

internal object ClashYamlDocument {
    private const val MERGE_KEY = "<<"
    private const val MAX_ALIASES = 50
    private const val MAX_CHARS = 10 * 1024 * 1024

    fun load(text: String): JsonElement? = runCatching {
        val settings = LoadSettings.builder()
            .setSchema(CoreSchema())
            .setMaxAliasesForCollections(MAX_ALIASES)
            .setCodePointLimit(MAX_CHARS)
            .setAllowDuplicateKeys(true)
            .build()
        toJson(Load(settings).loadFromString(text))
    }.getOrNull()

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(mergedEntries(value).mapValues { toJson(it.value) })
        is List<*> -> JsonArray(value.map(::toJson))
        is Boolean -> JsonPrimitive(value)
        is Int, is Long, is BigInteger, is Float, is Double -> JsonPrimitive(value as Number)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun mergedEntries(map: Map<*, *>): Map<String, Any?> {
        val own = map.entries.filter { it.key.toString() != MERGE_KEY }.associate { it.key.toString() to it.value }
        val merged = when (val source = map[MERGE_KEY]) {
            is Map<*, *> -> mergedEntries(source)
            is List<*> -> source.filterIsInstance<Map<*, *>>().asReversed().fold(emptyMap<String, Any?>()) { acc, item -> acc + mergedEntries(item) }
            else -> emptyMap()
        }
        return merged + own
    }
}
