package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.rulelist.InstalledSet
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ServiceClient private constructor(
    private val channel: SocketChannel,
    private val onLog: (level: String, message: String) -> Unit
) : AutoCloseable {

    data class Hello(val version: String, val core: String, val workDir: File)

    data class Traffic(val up: Long, val down: Long)

    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonObject>>()
    private val writeLock = Any()

    @Volatile var isOpen: Boolean = true
        private set

    init {
        thread(isDaemon = true, name = "erkin-service-reader") { readLoop() }
    }

    fun hello(): Hello {
        val reply = call("hello")
        return Hello(
            version = reply.string("version"),
            core = reply.string("core"),
            workDir = File(reply.string("workDir"))
        )
    }

    fun check(config: String) = call("check", timeout = CHECK_TIMEOUT) { put("config", config) }.throwIfFailed()

    fun start(config: String, resolvedProxy: String, resolver: String) = call("start", timeout = START_TIMEOUT) {
        put("config", config)
        put("resolved", resolvedProxy)
        put("resolver", resolver)
    }.throwIfFailed()

    fun stop() = call("stop", timeout = STOP_TIMEOUT).throwIfFailed()

    fun clearLog() = call("clearLog").throwIfFailed()

    fun subscribeLog() = call("logs").throwIfFailed()

    fun importRuleList(key: String, path: String, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet {
        val reply = call("rulelist", timeout = RULE_LIST_TIMEOUT) {
            put("key", key)
            put("path", path)
            put("format", format.wire)
            kind?.let { put("behavior", it.behavior) }
        }
        reply.throwIfFailed()
        val installed = checkNotNull(RuleSetKind.fromBehavior(reply.string("kind"))) { "Unknown rule list kind" }
        return InstalledSet(installed, reply["count"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    fun removeRuleList(name: String) = call("rulelistRemove") { put("name", name) }.throwIfFailed()

    fun urlTest(proxies: String, url: String, timeout: Duration, ipv6: Boolean, runningOnly: Boolean): String = call("urltest", timeout = timeout + URL_TEST_MARGIN) {
        put("proxies", proxies)
        put("url", url)
        put("timeout", timeout.inWholeMilliseconds.toInt())
        put("ipv6", ipv6)
        put("runningOnly", runningOnly)
    }.let { reply ->
        reply.throwIfFailed()
        checkNotNull(reply["delays"]) { "ERKIN service returned no delays" }.toString()
    }

    fun testSites(port: Int, resolver: String, sites: List<String>, timeout: Duration, ipv6: Boolean): String =
        call("sites", timeout = SITES_TEST_TIMEOUT) {
            put("port", port)
            put("resolver", resolver)
            put("sites", sites.joinToString(" "))
            put("timeout", timeout.inWholeMilliseconds.toInt())
            put("ipv6", ipv6)
        }.let { reply ->
            reply.throwIfFailed()
            checkNotNull(reply["sites"]) { "ERKIN service returned no site results" }.toString()
        }

    fun traffic(): Traffic? = call("traffic").takeIf { it.ok }?.let { reply ->
        Traffic(reply["up"]?.jsonPrimitive?.longOrNull ?: 0, reply["down"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    fun connections(): String = call("connections").let { reply ->
        reply.throwIfFailed()
        checkNotNull(reply["table"]) { "ERKIN service returned no connection table" }.toString()
    }

    override fun close() {
        isOpen = false
        runCatching { channel.close() }
        pending.values.forEach { it.completeExceptionally(IOException("ERKIN service connection closed")) }
        pending.clear()
    }

    private fun call(
        op: String,
        timeout: Duration = DEFAULT_TIMEOUT,
        fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}
    ): JsonObject {
        check(isOpen) { "ERKIN service connection closed" }
        val id = ids.incrementAndGet()
        val future = CompletableFuture<JsonObject>()
        pending[id] = future
        val message = buildJsonObject {
            put("id", id)
            put("op", op)
            fields()
        }
        val bytes = (message.toString() + "\n").encodeToByteArray()
        try {
            synchronized(writeLock) {
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
            return future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            pending.remove(id)
            throw (t.cause as? IOException) ?: t
        }
    }

    private fun readLoop() {
        runCatching {
            Channels.newInputStream(channel).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val reply = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                    (reply["log"] as? JsonObject)?.let { event ->
                        runCatching { onLog(event.string("level"), event.string("message")) }
                        return@forEach
                    }
                    val id = reply["id"]?.jsonPrimitive?.longOrNull ?: return@forEach
                    pending.remove(id)?.complete(reply)
                }
            }
        }
        close()
    }

    private val JsonObject.ok: Boolean get() = this["ok"]?.jsonPrimitive?.booleanOrNull == true

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.throwIfFailed() {
        val error = string("error")
        if (error.isNotEmpty()) throw IllegalStateException(error)
    }

    companion object {
        private val DEFAULT_TIMEOUT = 10_000.milliseconds
        private val CHECK_TIMEOUT = 30_000.milliseconds
        private val START_TIMEOUT = 120_000.milliseconds
        private val STOP_TIMEOUT = 30_000.milliseconds
        private val URL_TEST_MARGIN = 2_000.milliseconds
        private val SITES_TEST_TIMEOUT = 60_000.milliseconds
        private val RULE_LIST_TIMEOUT = 60_000.milliseconds

        fun connect(socket: File, onLog: (level: String, message: String) -> Unit = { _, _ -> }): ServiceClient {
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                channel.connect(UnixDomainSocketAddress.of(socket.toPath()))
            } catch (t: Throwable) {
                channel.close()
                throw t
            }
            return ServiceClient(channel, onLog)
        }
    }
}
