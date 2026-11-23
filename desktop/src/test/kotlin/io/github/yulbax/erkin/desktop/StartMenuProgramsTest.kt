package io.github.yulbax.erkin.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class StartMenuProgramsTest {
    private fun shortcut(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/shortcuts/$name.lnk")).use { it.readBytes() }

    @Test
    fun theTargetOfAShortcutIsRead() {
        assertEquals("C:\\Windows\\System32\\notepad.exe", ShellLink.target(shortcut("notepad")) { null })
    }

    @Test
    fun aTargetOutsideTheAnsiCodePageIsReadFromItsUnicodePath() {
        assertEquals(
            "C:\\Users\\erkin\\AppData\\Local\\Programs\\Тест клиент\\Клиент.exe",
            ShellLink.target(shortcut("cyrillic-path")) { null }
        )
    }

    @Test
    fun aTargetGivenThroughAVariableIsResolved() {
        assertEquals("C:\\Windows\\notepad.exe", ShellLink.target(shortcut("env-notepad")) { if (it == "windir") "C:\\Windows" else null })
    }

    @Test
    fun anythingButAShortcutHasNoTarget() {
        assertEquals(null, ShellLink.target(ByteArray(10)) { null })
        assertEquals(null, ShellLink.target("not a shortcut at all, but long enough to pass the header length check of 76".toByteArray()) { null })
    }

    @Test
    fun theCatalogKeepsInstalledProgramsAndDropsToolsUninstallersAndDuplicates() {
        val root = Files.createTempDirectory("erkin-start").toFile()
        try {
            fun program(path: String) = File(root, path).apply { parentFile.mkdirs(); writeText("") }
            val telegram = program("Programs/Telegram/Telegram.exe")
            val uninstaller = program("Programs/Telegram/unins000.exe")
            val tool = program("Windows/System32/mstsc.exe")
            val readme = program("Programs/Telegram/readme.txt")
            val targets = mapOf(
                "Telegram" to telegram,
                "Telegram Desktop" to telegram,
                "Uninstall Telegram" to uninstaller,
                "Remote Desktop Connection" to tool,
                "Readme" to readme,
                "Gone" to File(root, "Programs/Gone/gone.exe")
            )
            val programs = StartMenuPrograms.programs(
                shortcuts = targets.keys.map { File("$it.lnk") },
                target = { targets[it.nameWithoutExtension]?.path },
                excludedRoots = listOf(File(root, "Windows").path)
            )
            assertEquals(listOf(StartMenuProgram("Telegram", telegram)), programs)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aCataloguedProgramAlreadyListedIsNotRepeated() {
        val listed = listOf(io.github.yulbax.erkin.data.InstalledApp("C:\\Apps\\Zoom\\Zoom.exe", "Zoom", false, true, "C:\\Apps\\Zoom\\Zoom.exe"))
        val programs = listOf(
            StartMenuProgram("Zoom Workplace", File("C:\\apps\\zoom\\Zoom.exe")),
            StartMenuProgram("Telegram", File("C:\\Apps\\Telegram\\Telegram.exe"))
        )
        val added = ProcessInstalledApps.catalogued(programs, listed, ownExecutable = "ERKIN.exe")

        assertEquals(listOf("Telegram"), added.map { it.name })
    }
}
