package io.github.yulbax.erkin.desktop

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HostDataImportTest {
    private val root = Files.createTempDirectory("erkin-import").toFile()
    private val host = File(root, "host").apply { mkdirs() }
    private val dir = File(root, "sandbox").apply { mkdirs() }

    @After
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun aFreshSandboxTakesTheDatabaseIdentityAndLanguageButNotTheLogs() {
        listOf("erkin.db", "erkin.db-wal", "erkin.db-shm", "device-id", "language", "erkin.log").forEach { File(host, it).writeText(it) }
        File(dir, "device-id").writeText("generated")

        HostDataImport.into(dir, host)

        listOf("erkin.db", "erkin.db-wal", "device-id", "language").forEach { assertEquals(it, File(dir, it).readText()) }
        assertFalse(File(dir, "erkin.db-shm").exists())
        assertFalse(File(dir, "erkin.log").exists())
    }

    @Test
    fun aSandboxWithItsOwnDatabaseKeepsIt() {
        File(host, "erkin.db").writeText("host")
        File(host, "device-id").writeText("host")
        File(dir, "erkin.db").writeText("own")

        HostDataImport.into(dir, host)

        assertEquals("own", File(dir, "erkin.db").readText())
        assertFalse(File(dir, "device-id").exists())
    }

    @Test
    fun nothingIsCopiedOutsideASandboxOrWithoutAHostDatabase() {
        File(host, "device-id").writeText("host")

        HostDataImport.into(dir, null)
        HostDataImport.into(dir, host)

        assertFalse(File(dir, "device-id").exists())
    }
}
