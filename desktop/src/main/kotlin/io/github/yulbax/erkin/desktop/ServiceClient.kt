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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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

    fun check(config: String) = call("check", timeoutMs = CHECK_TIMEOUT_MS) { put("config", config) }.throwIfFailed()

    fun start(config: String, resolvedProxy: String, resolver: String) = call("start", timeoutMs = START_TIMEOUT_MS) {
        put("config", config)
        put("resolved", resolvedProxy)
        put("resolver", resolver)
    }.throwIfFailed()

    fun stop() = call("stop", timeoutMs = STOP_TIMEOUT_MS).throwIfFailed()

    fun clearLog() = call("clearLog").throwIfFailed()

    fun subscribeLog() = call("logs").throwIfFailed()

    fun importRuleList(key: String, path: String, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet {
        val reply = call("rulelist", timeoutMs = RULE_LIST_TIMEOUT_MS) {
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

    fun urlTest(proxies: String, url: String, timeoutMs: Int): String = call("urltest", timeoutMs = URL_TEST_TIMEOUT_MS) {
        put("proxies", proxies)
        put("url", url)
        put("timeout", timeoutMs)
    }.let { reply ->
        reply.throwIfFailed()
        checkNotNull(reply["delays"]) { "ERKIN service returned no delays" }.toString()
    }

    fun delay(proxy: String, url: String, timeoutMs: Int): Int? = call("delay", timeoutMs = timeoutMs + 2_000L) {
        put("proxy", proxy)
        put("url", url)
        put("timeout", timeoutMs)
    }.let { reply -> reply["delay"]?.jsonPrimitive?.intOrNull?.takeIf { reply.ok && it > 0 } }

    fun traffic(): Traffic? = call("traffic").takeIf { it.ok }?.let { reply ->
        Traffic(reply["up"]?.jsonPrimitive?.longOrNull ?: 0, reply["down"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    override fun close() {
        isOpen = false
        runCatching { channel.close() }
        pending.values.forEach { it.completeExceptionally(IOException("ERKIN service connection closed")) }
        pending.clear()
    }

    private fun call(
        op: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
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
            return future.get(timeoutMs, TimeUnit.MILLISECONDS)
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
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val CHECK_TIMEOUT_MS = 30_000L
        private const val START_TIMEOUT_MS = 120_000L
        private const val STOP_TIMEOUT_MS = 30_000L
        private const val URL_TEST_TIMEOUT_MS = 120_000L
        private const val RULE_LIST_TIMEOUT_MS = 60_000L

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
