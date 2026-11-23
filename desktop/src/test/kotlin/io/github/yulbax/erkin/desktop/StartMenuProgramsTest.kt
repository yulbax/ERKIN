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
    fun theStartMenuShortcutsAreFoundByTheirNames() {
        val targets = StartMenuPrograms.targets(listOf(File("Remote Desktop Connection.lnk"), File("Broken.lnk"))) {
            if (it.name.startsWith("Remote")) "C:\\Windows\\system32\\mstsc.exe" else null
        }
        assertEquals(mapOf("remote desktop connection" to "C:\\Windows\\system32\\mstsc.exe"), targets)
    }

    @Test
    fun windowsListsWhatItsStartMenuShowsAndResolvesEachKind() {
        val env = mapOf("SystemRoot" to "C:\\Windows", "ProgramFiles(x86)" to "C:\\Program Files (x86)")
        val apps = listOf(
            StartApp("Calculator", "Microsoft.WindowsCalculator_8wekyb3d8bbwe!App",
                path = "C:\\Program Files\\WindowsApps\\Microsoft.WindowsCalculator_11.2607.0.0_x64__8wekyb3d8bbwe\\CalculatorApp.exe"),
            StartApp("Command Prompt", "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\cmd.exe"),
            StartApp("Documentation", "{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}\\Kits\\Docs.url"),
            StartApp("Microsoft Edge", "MSEdge"),
            StartApp("File Explorer", "Microsoft.Windows.Explorer"),
            StartApp("Control Panel", "Microsoft.Windows.ControlPanel"),
            StartApp("Uninstall Tool", "{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}\\Tool\\unins000.exe")
        )
        val shortcuts = mapOf("microsoft edge" to "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe")
        val catalog = WindowsStartApps.catalog(apps, shortcuts, env::get) { true }

        assertEquals(
            listOf(
                "Calculator" to "C:\\Program Files\\WindowsApps\\Microsoft.WindowsCalculator_11.2607.0.0_x64__8wekyb3d8bbwe\\CalculatorApp.exe",
                "Command Prompt" to "C:\\Windows\\System32\\cmd.exe",
                "Microsoft Edge" to "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "File Explorer" to "C:\\Windows\\explorer.exe"
            ),
            catalog.map { it.name to it.path }
        )
    }

    @Test
    fun theStartAppsAnswerIsRead() {
        val output = "\uFEFF[{\"name\":\"Calculator\",\"id\":\"Microsoft.WindowsCalculator_8wekyb3d8bbwe!App\",\"path\":\"C:\\\\Calc.exe\",\"logo\":\"\"}," +
            "{\"name\":\"Edge\",\"id\":\"MSEdge\",\"path\":null},{\"name\":\"\",\"id\":\"x\"}]"
        assertEquals(
            listOf(
                StartApp("Calculator", "Microsoft.WindowsCalculator_8wekyb3d8bbwe!App", "C:\\Calc.exe"),
                StartApp("Edge", "MSEdge")
            ),
            WindowsStartApps.parse(output)
        )
    }

    @Test
    fun aStoreAppKeepsOneIdAcrossItsUpdates() {
        assertEquals(
            "C:\\Program Files\\WindowsApps\\Microsoft.WindowsNotepad_*_8wekyb3d8bbwe\\Notepad\\Notepad.exe",
            WindowsAppIds.stable("C:\\Program Files\\WindowsApps\\Microsoft.WindowsNotepad_11.2609.11.0_x64__8wekyb3d8bbwe\\Notepad\\Notepad.exe")
        )
        assertEquals("C:\\Apps\\Zoom.exe", WindowsAppIds.stable("C:\\Apps\\Zoom.exe"))
    }

    @Test
    fun theLargestIconWithoutABackplateIsChosen() {
        val dir = Files.createTempDirectory("erkin-logo").toFile()
        try {
            listOf(
                "AppList.scale-200.png", "AppList.targetsize-48.png", "AppList.targetsize-256.png",
                "AppList.targetsize-48_altform-unplated.png", "AppList.targetsize-256_altform-lightunplated.png",
                "AppList.targetsize-256_contrast-black.png", "Other.png"
            ).forEach { File(dir, it).writeText("") }
            assertEquals("AppList.targetsize-48_altform-unplated.png", WindowsStartApps.logoFile(File(dir, "AppList.png").path)?.name)
            File(dir, "AppList.targetsize-48_altform-unplated.png").delete()
            assertEquals("AppList.targetsize-256.png", WindowsStartApps.logoFile(File(dir, "AppList.png").path)?.name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
