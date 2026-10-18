package io.github.yulbax.erkin.rulelist

import io.github.yulbax.erkin.data.rules.RuleListDao
import io.github.yulbax.erkin.data.rules.RuleListEntity
import io.github.yulbax.erkin.vpn.DownloadRoute
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface RuleListStore {
    fun installed(fileName: String): File?

    fun stagingDir(): File

    suspend fun install(key: String, source: File, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet

    suspend fun remove(fileName: String)
}

data class InstalledSet(val kind: RuleSetKind, val count: Long)

data class InstalledRuleList(val kinds: Set<RuleSetKind>, val updatedAt: Long)

data class RuleListState(
    val name: String? = null,
    val installed: InstalledRuleList? = null,
    val title: String? = null,
    val entries: Long = 0,
    val error: RuleListError? = null,
    val downloading: Boolean = false
)

class RuleListDatabase(
    private val store: RuleListStore,
    private val dao: RuleListDao,
    private val route: DownloadRoute = DownloadRoute()
) {
    private val mutex = Mutex()
    private val downloading = MutableStateFlow(emptySet<String>())
    private val revision = MutableStateFlow(0)

    val states: Flow<Map<String, RuleListState>> =
        combine(dao.observeAll(), revision, downloading) { records, _, active ->
            val byUrl = records.associateBy { it.source }
            (byUrl.keys + active).associateWith { url ->
                val record = byUrl[url]
                RuleListState(
                    name = record?.name,
                    installed = installed(url),
                    title = record?.title,
                    entries = record?.entries ?: 0,
                    error = record?.failure?.let(RuleListFailure::fromWire)?.let { RuleListError(it, record.failureDetail) },
                    downloading = url in active
                )
            }
        }.flowOn(Dispatchers.IO)

    fun installed(url: String): InstalledRuleList? {
        val files = RuleSetKind.entries.mapNotNull { kind ->
            store.installed(RuleLists.fileName(url, kind))?.takeIf { it.isFile }?.let { kind to it }
        }
        if (files.isEmpty()) return null
        return InstalledRuleList(kinds = files.mapTo(HashSet()) { it.first }, updatedAt = files.maxOf { it.second.lastModified() })
    }

    suspend fun add(source: RuleListSource, name: String? = null): Result<Unit> {
        withContext(Dispatchers.IO) {
            dao.insertIfAbsent(RuleListEntity(source = source.url, name = name, kind = source.kind?.behavior))
        }
        return download(source.url)
    }

    suspend fun download(url: String): Result<Unit> {
        downloading.update { it + url }
        return try {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    val kind = RuleSetKind.fromBehavior(dao.get(url)?.kind)
                    install(RuleListSource(url, kind))
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = error as? RuleListException ?: RuleListException(RuleListFailure.DOWNLOAD, error.message ?: error::class.simpleName)
            withContext(Dispatchers.IO) {
                val record = dao.get(url) ?: RuleListEntity(url)
                dao.upsert(record.copy(failure = failure.failure.wire, failureDetail = failure.detail))
            }
            Result.failure(failure)
        } finally {
            revision.update { it + 1 }
            downloading.update { it - url }
        }
    }

    suspend fun rename(url: String, name: String?) = withContext(Dispatchers.IO) {
        dao.rename(url, name?.trim()?.takeIf { it.isNotEmpty() })
    }

    suspend fun delete(url: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            RuleSetKind.entries.forEach { runCatching { store.remove(RuleLists.fileName(url, it)) } }
            dao.delete(url)
            revision.update { it + 1 }
        }
    }

    private suspend fun install(source: RuleListSource) {
        val url = source.url
        val key = RuleLists.key(url)
        val dir = store.stagingDir().apply { mkdirs() }
        val raw = File(dir, key + DOWNLOAD_SUFFIX)
        val staged = mutableListOf(raw)
        try {
            fetch(url, raw)
            var title: String? = null
            val sets = when (RuleListContent.of(raw)) {
                RuleListContent.MRS -> listOf(installSet(key, raw, RuleSetFormat.MRS, source.kind))
                RuleListContent.TEXT -> {
                    val parsed = raw.bufferedReader().useLines(RuleListParser::parse)
                    title = parsed.title
                    val parts = RuleSetKind.entries.filter { source.kind == null || it == source.kind }
                        .associateWith { kind -> parsed.entries(kind) }
                        .filterValues { it.isNotEmpty() }
                    if (parts.isEmpty()) {
                        throw RuleListException(if (parsed.size > 0) RuleListFailure.WRONG_KIND else RuleListFailure.EMPTY)
                    }
                    parts.map { (kind, entries) ->
                        val text = File(dir, "$key.${kind.behavior}$TEXT_SUFFIX").also(staged::add)
                        text.bufferedWriter().use { out -> entries.forEach { out.appendLine(it) } }
                        installSet(key, text, RuleSetFormat.TEXT, kind)
                    }
                }
                RuleListContent.OTHER -> throw RuleListException(RuleListFailure.NOT_A_LIST)
            }
            val kinds = sets.mapTo(HashSet()) { it.kind }
            RuleSetKind.entries.filterNot { it in kinds }.forEach { store.remove(RuleLists.fileName(url, it)) }
            val record = dao.get(url) ?: RuleListEntity(url)
            dao.upsert(record.copy(title = title, entries = sets.sumOf { it.count }, failure = null, failureDetail = null))
        } finally {
            staged.forEach(File::delete)
        }
    }

    private suspend fun installSet(key: String, file: File, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet =
        try {
            store.install(key, file, format, kind)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = RuleListFailure.fromWire(error.message) ?: RuleListFailure.SAVE
            throw RuleListException(failure, error.message.takeIf { failure == RuleListFailure.SAVE })
        }

    private fun ParsedRuleList.entries(kind: RuleSetKind): List<String> = when (kind) {
        RuleSetKind.DOMAIN -> domains
        RuleSetKind.IPCIDR -> cidrs
    }

    private suspend fun fetch(url: String, target: File) {
        val context = currentCoroutineContext()
        try {
            route.fetch(url) { connection ->
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw RuleListException(RuleListFailure.DOWNLOAD, "HTTP $code")
                    if (connection.contentType.orEmpty().startsWith(HTML_TYPE)) throw RuleListException(RuleListFailure.NOT_A_LIST)
                    if (connection.contentLengthLong > MAX_BYTES) throw RuleListException(RuleListFailure.TOO_LARGE)
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var done = 0L
                            while (true) {
                                context.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                done += read
                                if (done > MAX_BYTES) throw RuleListException(RuleListFailure.TOO_LARGE)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (error: RuleListException) {
            throw error
        } catch (error: IOException) {
            throw RuleListException(RuleListFailure.DOWNLOAD, error.message ?: error::class.simpleName)
        }
    }

    companion object {
        const val MAX_BYTES = 32L shl 20
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTML_TYPE = "text/html"
        private const val DOWNLOAD_SUFFIX = ".download"
        private const val TEXT_SUFFIX = ".txt"
    }
}

internal enum class RuleListContent {
    MRS,
    TEXT,
    OTHER;

    companion object {
        private const val SNIFF_BYTES = 8 * 1024
        private val ZSTD = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
        private val BINARY_MAGIC = listOf(
            byteArrayOf(0x50, 0x4B, 0x03, 0x04),
            byteArrayOf(0x1F, 0x8B.toByte()),
            byteArrayOf(0x25, 0x50, 0x44, 0x46),
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        )
        private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        fun of(file: File): RuleListContent {
            val head = ByteArray(SNIFF_BYTES)
            val read = file.inputStream().use { it.read(head) }
            return of(head.copyOf(read.coerceAtLeast(0)))
        }

        fun of(head: ByteArray): RuleListContent = when {
            head.startsWith(ZSTD) -> MRS
            head.isEmpty() || BINARY_MAGIC.any { head.startsWith(it) } || head.any { it == 0.toByte() } -> OTHER
            head.dropBom().firstOrNull { !it.toInt().toChar().isWhitespace() } == '<'.code.toByte() -> OTHER
            else -> TEXT
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun ByteArray.dropBom(): List<Byte> = if (startsWith(BOM)) drop(BOM.size) else toList()
    }
}
