package io.github.yulbax.erkin.rulelist

import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface RuleListStore {
    fun installed(fileName: String): File?

    fun stagingDir(): File

    suspend fun install(files: Map<String, File?>)
}

data class InstalledRuleList(
    val kinds: Set<RuleSetKind>,
    val updatedAt: Long,
    val title: String?,
    val entries: Int
)

class RuleListDatabase(private val store: RuleListStore) {
    private val mutex = Mutex()
    private val _downloading = MutableStateFlow(emptySet<String>())
    val downloading: StateFlow<Set<String>> = _downloading.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun installed(url: String): InstalledRuleList? {
        val files = RuleSetKind.entries.mapNotNull { kind ->
            store.installed(RuleLists.fileName(url, kind))?.takeIf { it.isFile }?.let { kind to it }
        }
        if (files.isEmpty()) return null
        val headers = files.map { (_, file) -> Header.read(file) }
        return InstalledRuleList(
            kinds = files.mapTo(HashSet()) { it.first },
            updatedAt = files.maxOf { it.second.lastModified() },
            title = headers.firstNotNullOfOrNull { it.title },
            entries = headers.sumOf { it.entries }
        )
    }

    suspend fun download(url: String): Result<InstalledRuleList> {
        _downloading.update { it + url }
        val staged = mutableListOf<File>()
        return try {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    val dir = store.stagingDir().apply { mkdirs() }
                    val raw = File(dir, RuleLists.key(url) + DOWNLOAD_SUFFIX).also(staged::add)
                    fetch(url, raw)
                    val parsed = raw.bufferedReader().useLines(RuleListParser::parse)
                    raw.delete()
                    if (parsed.size == 0) throw IOException("The list has no domains or IP addresses")
                    store.install(RuleSetKind.entries.associate { kind ->
                        val name = RuleLists.fileName(url, kind)
                        name to write(File(dir, name + STAGED_SUFFIX).also(staged::add), parsed, kind)
                    })
                }
            }
            _revision.update { it + 1 }
            Result.success(checkNotNull(installed(url)) { "The list was not saved" })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            staged.forEach(File::delete)
            _downloading.update { it - url }
        }
    }

    private fun write(target: File, parsed: ParsedRuleList, kind: RuleSetKind): File? {
        val entries = when (kind) {
            RuleSetKind.DOMAIN -> parsed.domains
            RuleSetKind.IPCIDR -> parsed.cidrs
        }
        if (entries.isEmpty()) return null
        target.bufferedWriter().use { out ->
            Header.write(out, parsed.title, entries.size)
            entries.forEach { out.appendLine(it) }
        }
        return target
    }

    private suspend fun fetch(url: String, target: File) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("The list is too large")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > MAX_BYTES) throw IOException("The list is too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private class Header(val title: String?, val entries: Int) {
        companion object {
            private const val TITLE = "# title: "
            private const val ENTRIES = "# entries: "
            private const val LINES = 2

            fun write(out: Appendable, title: String?, entries: Int) {
                title?.let { out.append(TITLE).append(it).append('\n') }
                out.append(ENTRIES).append(entries.toString()).append('\n')
            }

            fun read(file: File): Header {
                val lines = runCatching { file.bufferedReader().useLines { it.take(LINES).toList() } }.getOrDefault(emptyList())
                return Header(
                    title = lines.firstOrNull { it.startsWith(TITLE) }?.removePrefix(TITLE),
                    entries = lines.firstOrNull { it.startsWith(ENTRIES) }?.removePrefix(ENTRIES)?.toIntOrNull() ?: 0
                )
            }
        }
    }

    companion object {
        const val MAX_BYTES = 32L shl 20
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val DOWNLOAD_SUFFIX = ".download"
        private const val STAGED_SUFFIX = ".staged"
    }
}
