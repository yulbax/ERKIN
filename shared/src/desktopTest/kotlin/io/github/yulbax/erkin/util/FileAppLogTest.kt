package io.github.yulbax.erkin.util

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAppLogTest {
    private val dir = Files.createTempDirectory("app-log").toFile()
    private val file = File(dir, "erkin.log")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun entriesStartWithTheirSource() {
        val log = FileAppLog(file)

        log.i("VpnSession", "started")
        log.of(LogSource.BYEDPI).w("ByeDpi", "exited")
        log.core("warning", "dial failed")

        val entries = log.entries.value
        assertEquals(entries.toString(), 3, entries.size)
        assertTrue(entries[0].matches(Regex("""\[app] \d{2}-\d{2} [\d:.]{12} I/VpnSession: started""")))
        assertTrue(entries[1].matches(Regex("""\[byedpi] \d{2}-\d{2} [\d:.]{12} W/ByeDpi: exited""")))
        assertTrue(entries[2].matches(Regex("""\[core] \d{2}-\d{2} [\d:.]{12} W: dial failed""")))
    }

    @Test
    fun reloadingKeepsStackTracesWithTheirEntry() {
        file.writeText(
            "10-17 19:02:25.367 E/VpnController: Reload failed\n" +
                "java.lang.IllegalStateException: Could not create NetworkMonitor\n" +
                "    at android.os.Parcel.readException(Parcel.java:3324)\n" +
                "[core] 10-17 19:02:26.001 W: dial failed\n"
        )

        val entries = FileAppLog(file).entries.value

        assertEquals(2, entries.size)
        assertTrue(entries[0].endsWith("(Parcel.java:3324)"))
        assertEquals("[core] 10-17 19:02:26.001 W: dial failed", entries[1])
    }
}
