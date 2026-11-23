package io.github.yulbax.erkin.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxApplicationsTest {

    @Test
    fun onlyTheMainEntryOfALauncherIsRead() {
        val entry = LinuxApplications.parse(
            """
            [Desktop Entry]
            Type=Application
            Name=Ghostty
            Name[ru]=Гостти
            Exec=ghostty --gtk-single-instance=true
            Icon=com.mitchellh.ghostty

            [Desktop Action new-window]
            Name=New Window
            Exec=ghostty --new-window
            """.trimIndent(),
            language = "ru"
        )
        assertEquals(DesktopEntry("Гостти", "ghostty --gtk-single-instance=true", "com.mitchellh.ghostty", hidden = false), entry)
    }

    @Test
    fun aFlatpakLauncherNamesItsApp() {
        val entry = LinuxApplications.parse(
            "[Desktop Entry]\nType=Application\nName=Telegram\nExec=/usr/bin/flatpak run --branch=stable --command=Telegram org.telegram.desktop -- %u\n" +
                "Icon=org.telegram.desktop\nX-Flatpak=org.telegram.desktop"
        )
        assertEquals("org.telegram.desktop", entry?.flatpak)
    }

    @Test
    fun aFlatpakProcessIsKnownByItsAppInsideAndAroundItsSandbox() {
        val info = "[Application]\nname=org.telegram.desktop\nruntime=runtime/org.kde.Platform/x86_64/6.9\n\n[Instance]\nname=other"
        val scope = "0::/user.slice/user-1000.slice/user@1000.service/app.slice/app-flatpak-org.telegram.desktop-1443230209.scope\n"
        assertEquals("org.telegram.desktop", FlatpakApps.appOf(info, ""))
        assertEquals("org.telegram.desktop", FlatpakApps.appOf("", scope))
        assertNull(FlatpakApps.appOf("", "0::/user.slice/user-1000.slice/session-3.scope\n"))
    }

    @Test
    fun linksAndHiddenEntriesAreNotApplications() {
        assertNull(LinuxApplications.parse("[Desktop Entry]\nType=Link\nName=Docs\nURL=https://example.org"))
        assertTrue(LinuxApplications.parse("[Desktop Entry]\nType=Application\nName=X\nExec=x\nNoDisplay=true")!!.hidden)
    }

    @Test
    fun theProgramIsTheFirstWordAfterTheEnvironment() {
        assertEquals("/opt/zen/zen-bin", LinuxApplications.programOf("/opt/zen/zen-bin %u"))
        assertEquals("steam", LinuxApplications.programOf("GDK_SCALE=2 steam %U"))
        assertEquals("/opt/My App/app", LinuxApplications.programOf("\"/opt/My App/app\" --flag"))
        assertNull(LinuxApplications.programOf("flatpak run org.telegram.desktop"))
        assertNull(LinuxApplications.programOf("env FOO=1 tool"))
    }

    @Test
    fun aWrapperScriptLeadsToTheProgramItStarts() {
        val root = Files.createTempDirectory("erkin-wrapper").toFile()
        try {
            val program = File(root, "opt/zen/zen-bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())); setExecutable(true) }
            val wrapper = File(root, "zen-browser").apply { writeText("#!/bin/bash\n# starts ${'$'}{HOME}/notes\nexec ${program.path} \"${'$'}@\"\n"); setExecutable(true) }
            assertEquals(program, LinuxApplications.scriptTarget(wrapper))
            assertNull(LinuxApplications.scriptTarget(program))
        } finally {
            root.deleteRecursively()
        }
    }
}
