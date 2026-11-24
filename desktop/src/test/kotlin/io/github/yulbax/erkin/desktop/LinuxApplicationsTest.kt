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
    fun theCatalogIsReadThroughTheHostAndNamesHostPaths() {
        val root = Files.createTempDirectory("erkin-host").toFile()
        try {
            fun file(path: String) = File(root, path).apply { parentFile.mkdirs() }
            fun binary(path: String) = file(path).apply { writeBytes(ELF); setExecutable(true) }
            fun link(path: String, target: String) = Files.createSymbolicLink(file(path).toPath(), File(target).toPath())

            link("bin", "usr/bin")
            binary("opt/zen/zen-bin")
            file("usr/bin/zen-browser").apply { writeText("#!/bin/bash\n# starts ${'$'}{HOME}/notes\nexec /opt/zen/zen-bin \"${'$'}@\"\n"); setExecutable(true) }
            binary("usr/bin/term-real")
            link("usr/bin/term", "/usr/bin/term-real")
            link("usr/bin/kinfo", "/usr/bin/term-real")
            file("usr/share/applications/kinfo.desktop").writeText("[Desktop Entry]\nType=Application\nName=Info\nExec=kinfo")
            file("usr/share/applications/zen.desktop").writeText("[Desktop Entry]\nType=Application\nName=Zen\nExec=zen-browser %u\nIcon=zen")
            file("usr/share/applications/term.desktop").writeText("[Desktop Entry]\nType=Application\nName=Term\nExec=/bin/term")
            file("var/lib/flatpak/exports/share/applications/org.telegram.desktop.desktop")
                .writeText("[Desktop Entry]\nType=Application\nName=Telegram\nExec=/usr/bin/flatpak run org.telegram.desktop\nX-Flatpak=org.telegram.desktop")
            val icon = file("usr/share/icons/hicolor/scalable/apps/zen.svg").apply { writeText("<svg/>") }

            val host = HostSystem(
                home = "/home/u",
                dataHome = "/home/u/.local/share",
                dataDirs = listOf("/var/lib/flatpak/exports/share", "/usr/share"),
                path = listOf("/usr/bin", "/bin"),
                visible = { root.path + it }
            )
            assertEquals(
                listOf(
                    Triple("Info", "/usr/bin/term-real", null),
                    Triple("Telegram", "org.telegram.desktop", null),
                    Triple("Term", "/usr/bin/term-real", null),
                    Triple("Zen", "/opt/zen/zen-bin", icon)
                ),
                LinuxApplications.list(host).map { Triple(it.name, it.id, it.icon) }.sortedBy { it.first }
            )
            assertEquals("/usr/bin/term-real", host.realPath("/bin/./term"))
            assertEquals("/usr/bin/zen-browser", host.realPath("/usr/bin/../bin/zen-browser"))
            assertNull(host.realPath("/usr/bin/missing"))
            assertNull(LinuxApplications.scriptTarget("/opt/zen/zen-bin", host))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aProgramTheSandboxCannotSeeIsTakenFromItsLauncher() {
        val root = Files.createTempDirectory("erkin-hidden").toFile()
        try {
            fun file(path: String) = File(root, path).apply { parentFile.mkdirs() }
            file("home/u/.local/share/applications/clion.desktop")
                .writeText("[Desktop Entry]\nType=Application\nName=CLion\nExec=\"/home/u/.local/share/JetBrains/Toolbox/apps/clion/bin/clion\" %u")
            file("home/u/.local/share/applications/gone.desktop").writeText("[Desktop Entry]\nType=Application\nName=Gone\nExec=/opt/gone/bin/gone")
            val host = HostSystem(
                home = "/home/u",
                dataHome = "/home/u/.local/share",
                dataDirs = emptyList(),
                path = emptyList(),
                visible = { root.path + it },
                hidden = { it.startsWith("/home/u/") && !File(root.path + it).exists() }
            )
            assertEquals(
                listOf("CLion" to "/home/u/.local/share/JetBrains/Toolbox/apps/clion/bin/clion"),
                LinuxApplications.list(host).map { it.name to it.id }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val ELF = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    }
}
