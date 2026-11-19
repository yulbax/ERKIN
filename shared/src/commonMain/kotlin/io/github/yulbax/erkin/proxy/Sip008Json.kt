package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal object Sip008JsonReader : FormatReader {
    private val REQUIRED = listOf("server", "server_port", "method", "password")

    override fun detects(text: String): Boolean = servers(parseJson(text)) != null

    override fun read(text: String): List<ParsedProfile> =
        servers(parseJson(text)).orEmpty().mapNotNull { server -> link(server)?.let(LinkParser::parse) }

    private fun servers(root: Any?): List<JsonObject>? {
        val items = ((root as? JsonObject)?.get("servers") as? JsonArray)?.takeIf { it.isNotEmpty() } ?: return null
        val objects = items.filterIsInstance<JsonObject>()
        return objects.takeIf { it.size == items.size && it.all { server -> REQUIRED.all { key -> key in server } } }
    }

    private fun link(server: JsonObject): String? = shadowsocksLink(
        host = server.text("server") ?: return null,
        port = server.int("server_port") ?: return null,
        method = server.text("method") ?: return null,
        password = server.text("password") ?: return null,
        plugin = server.text("plugin"),
        pluginOpts = server.text("plugin_opts"),
        name = server.text("remarks")
    )
}
