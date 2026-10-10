package io.github.yulbax.erkin.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessInstalledAppsTest {

    private fun discover(vararg paths: String) =
        ProcessInstalledApps.discover(paths.map(::File), ownExecutable = "ERKIN", systemRoots = listOf("/usr/"))

    @Test
    fun aSingleCopyIsKeyedByNameAndShowsItsPath() {
        val apps = discover("/opt/telegram/Telegram", "/opt/telegram/Telegram", "/opt/erkin/bin/ERKIN")
        assertEquals(1, apps.size)
        assertEquals("Telegram", apps.single().packageName)
        assertEquals("/opt/telegram/Telegram", apps.single().path)
    }

    @Test
    fun copiesWithTheSameNameGetTheirOwnPathEntriesNextToTheNameEntry() {
        val apps = discover("/usr/bin/python3", "/home/u/venv/bin/python3")
        assertEquals(listOf("python3", "/usr/bin/python3", "/home/u/venv/bin/python3"), apps.map { it.packageName })
        assertEquals(listOf(null, "/usr/bin/python3", "/home/u/venv/bin/python3"), apps.map { it.path })
        assertEquals(listOf(true, true, false), apps.map { it.isSystemApp })
    }
}
