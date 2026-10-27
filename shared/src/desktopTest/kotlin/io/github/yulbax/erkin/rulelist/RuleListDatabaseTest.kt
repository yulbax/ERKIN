package io.github.yulbax.erkin.rulelist

import androidx.room.Room
import com.sun.net.httpserver.HttpServer
import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.vpn.core.RuleListSource
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetFormat
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleListDatabaseTest {
    private val database = AppDatabase.build(Room.inMemoryDatabaseBuilder<AppDatabase>())
    private val root = Files.createTempDirectory("rule-lists").toFile()
    private val store = FakeStore(root)
    private val lists = RuleListDatabase(store, database.ruleListDao())
    private val responses = mutableMapOf<String, Response>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val response = responses[exchange.requestURI.path] ?: Response(404, "text/plain", ByteArray(0))
            exchange.responseHeaders.add("Content-Type", response.type)
            exchange.sendResponseHeaders(response.code, if (response.body.isEmpty()) -1 else response.body.size.toLong())
            exchange.responseBody.use { it.write(response.body) }
        }
        start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun aTextListIsSplitByKindAndRecordedWithItsTitle() = runBlocking {
        val source = serve("/ads.txt", "# Title: Ads\nads.example.com\n10.0.0.0/8\n")

        assertTrue(lists.add(source).isSuccess)

        assertEquals(setOf(RuleSetKind.DOMAIN, RuleSetKind.IPCIDR), lists.installed(source.url)?.kinds)
        assertEquals(listOf("+.ads.example.com"), store.installedText(source.url, RuleSetKind.DOMAIN))
        val state = lists.states.first().getValue(source.url)
        assertEquals("Ads", state.title)
        assertEquals(2L, state.entries)
        assertNull(state.error)
    }

    @Test
    fun downloadFailuresAreNamed() = runBlocking {
        val html = serve("/page", "<!doctype html><html></html>", "text/html; charset=utf-8")
        val htmlAsText = serve("/page.txt", "﻿  <html><body>blocked</body></html>")
        val binary = serve("/list.zip", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        val empty = serve("/empty.txt", "# nothing here\n! still nothing\n")
        val missing = RuleListSource(url("/missing.txt"), null)

        assertEquals(RuleListFailure.NOT_A_LIST, failureOf(html))
        assertEquals(RuleListFailure.NOT_A_LIST, failureOf(htmlAsText))
        assertEquals(RuleListFailure.NOT_A_LIST, failureOf(binary))
        assertEquals(RuleListFailure.EMPTY, failureOf(empty))
        assertEquals(RuleListFailure.DOWNLOAD, failureOf(missing))
        assertEquals("HTTP 404", lists.states.first().getValue(missing.url).error?.detail)
    }

    @Test
    fun aRenamedSetKeepsItsNameAcrossUpdates() = runBlocking {
        val source = serve("/ads.txt", "# Title: Ads\nads.example.com\n")
        lists.add(source, name = "Mine")
        lists.rename(source.url, "  Renamed ")
        lists.download(source.url)

        val state = lists.states.first().getValue(source.url)
        assertEquals("Renamed", state.name)
        assertEquals("Ads", state.title)
    }

    @Test
    fun aCountryListMustHoldAddresses() = runBlocking {
        val source = RuleListSource(serve("/ru.txt", "example.ru\n").url, RuleSetKind.IPCIDR)

        assertEquals(RuleListFailure.WRONG_KIND, failureOf(source))
        assertNull(lists.installed(source.url))
    }

    @Test
    fun aFailedUpdateKeepsTheWorkingVersion() = runBlocking {
        val source = serve("/ads.txt", "ads.example.com\n")
        assertTrue(lists.add(source).isSuccess)
        store.failure = "corrupt"

        assertEquals(RuleListFailure.CORRUPT, failureOf(source))

        val state = lists.states.first().getValue(source.url)
        assertNotNull(state.installed)
        assertEquals(1L, state.entries)
        assertEquals(RuleListError(RuleListFailure.CORRUPT, null), state.error)
        store.failure = null
        assertTrue(lists.add(source).isSuccess)
        assertNull(lists.states.first().getValue(source.url).error)
    }

    @Test
    fun onlyStaleOrMissingSetsAreRefreshed() = runBlocking {
        val fresh = serve("/fresh.txt", "fresh.example.com\n")
        val stale = serve("/stale.txt", "stale.example.com\n")
        assertTrue(lists.add(fresh).isSuccess)
        assertTrue(lists.add(stale).isSuccess)
        val missing = RuleListSource(url("/missing.txt"), null)
        lists.add(missing)
        val now = System.currentTimeMillis()
        store.modifiedAt(stale.url, now - RuleListDatabase.REFRESH_AFTER_MS)
        serve("/missing.txt", "missing.example.com\n")
        serve("/fresh.txt", "changed.example.com\n")
        serve("/stale.txt", "updated.example.com\n")

        assertTrue(lists.refreshStale(now))

        assertEquals(listOf("+.fresh.example.com"), store.installedText(fresh.url, RuleSetKind.DOMAIN))
        assertEquals(listOf("+.updated.example.com"), store.installedText(stale.url, RuleSetKind.DOMAIN))
        assertEquals(listOf("+.missing.example.com"), store.installedText(missing.url, RuleSetKind.DOMAIN))
        assertFalse(lists.refreshStale(now))
    }

    @Test
    fun anMrsDownloadIsHandedOverUnparsed() = runBlocking {
        val source = serve("/ru.mrs", byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x01))

        assertTrue(lists.add(RuleListSource(source.url, RuleSetKind.IPCIDR)).isSuccess)

        assertEquals(listOf(RuleSetFormat.MRS), store.formats)
    }

    @Test
    fun aDeletedSetLeavesTheLibrary() = runBlocking {
        val kept = serve("/kept.txt", "kept.example.com\n")
        val dropped = serve("/dropped.txt", "dropped.example.com\n")
        lists.add(kept)
        lists.add(dropped)

        lists.delete(dropped.url)

        assertNotNull(lists.installed(kept.url))
        assertNull(lists.installed(dropped.url))
        assertEquals(setOf(kept.url), lists.states.first().keys)
    }

    private suspend fun failureOf(source: RuleListSource): RuleListFailure? =
        (lists.add(source).exceptionOrNull() as? RuleListException)?.failure

    private fun serve(path: String, body: String, type: String = "text/plain") = serve(path, body.toByteArray(), type)

    private fun serve(path: String, body: ByteArray, type: String = "application/octet-stream"): RuleListSource {
        responses[path] = Response(200, type, body)
        return RuleListSource(url(path), null)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private class Response(val code: Int, val type: String, val body: ByteArray)

    private class FakeStore(root: File) : RuleListStore {
        private val installedDir = File(root, "installed").apply { mkdirs() }
        private val staging = File(root, "staging")
        var failure: String? = null
        val formats = mutableListOf<RuleSetFormat>()

        override fun installed(fileName: String): File = File(installedDir, fileName)

        override fun stagingDir(): File = staging

        override suspend fun install(key: String, source: File, format: RuleSetFormat, kind: RuleSetKind?): InstalledSet {
            failure?.let { throw IllegalStateException(it) }
            formats += format
            val installed = kind ?: RuleSetKind.DOMAIN
            source.copyTo(File(installedDir, "$key.${installed.behavior}.mrs"), overwrite = true)
            return InstalledSet(installed, source.readLines().count { it.isNotBlank() }.toLong())
        }

        override suspend fun remove(fileName: String) {
            File(installedDir, fileName).delete()
        }

        fun modifiedAt(url: String, time: Long) {
            RuleSetKind.entries.map { File(installedDir, RuleLists.fileName(url, it)) }.filter(File::isFile).forEach { it.setLastModified(time) }
        }

        fun installedText(url: String, kind: RuleSetKind): List<String> =
            File(installedDir, RuleLists.fileName(url, kind)).readLines()
    }
}
