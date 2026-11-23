package io.github.yulbax.erkin.desktop

import java.io.File
import java.util.Locale

data class DesktopEntry(
    val name: String,
    val command: String,
    val icon: String?,
    val hidden: Boolean,
    val flatpak: String? = null
)

object LinuxApplications {
    private const val ENTRY_GROUP = "[Desktop Entry]"
    private const val APPLICATION = "Application"
    private val FIELD_CODE = Regex("%[a-zA-Z%]")
    private val ENVIRONMENT_ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")
    private val SKIPPED_LAUNCHERS = setOf("env", "flatpak", "snap")
    private const val SCRIPT_MARK = "#!"
    private const val SCRIPT_READ_LIMIT = 64 * 1024
    private val SCRIPT_PATH = Regex("(/[A-Za-z0-9._+@/-]+)")

    fun list(): List<CatalogProgram> {
        val icons = IconThemes()
        return applicationDirs()
            .flatMap { dir -> dir.listFiles { file -> file.extension == "desktop" }.orEmpty().sortedBy { it.name } }
            .distinctBy { it.name }
            .mapNotNull { file -> runCatching { parse(file.readText()) }.getOrNull() }
            .filterNot { it.hidden }
            .mapNotNull { entry ->
                val icon = entry.icon?.let(icons::find)
                entry.flatpak?.let { CatalogProgram.flatpak(entry.name, it, icon) }
                    ?: executableOf(entry.command)?.let { CatalogProgram.executable(entry.name, it, icon) }
            }
    }

    fun parse(text: String, language: String = Locale.getDefault().language): DesktopEntry? {
        val fields = mutableMapOf<String, String>()
        var inEntry = false
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("[") -> inEntry = line == ENTRY_GROUP
                inEntry && '=' in line -> fields.putIfAbsent(line.substringBefore('=').trim(), line.substringAfter('=').trim())
            }
        }
        if (fields["Type"] != APPLICATION) return null
        val name = fields["Name[$language]"] ?: fields["Name"] ?: return null
        val command = fields["Exec"] ?: return null
        val hidden = fields["NoDisplay"].toBoolean() || fields["Hidden"].toBoolean()
        return DesktopEntry(name, command, fields["Icon"]?.ifBlank { null }, hidden, fields["X-Flatpak"]?.ifBlank { null })
    }

    fun programOf(command: String): String? =
        tokens(command.replace(FIELD_CODE, ""))
            .dropWhile { ENVIRONMENT_ASSIGNMENT.matches(it) }
            .firstOrNull()
            ?.takeUnless { File(it).name in SKIPPED_LAUNCHERS }

    private fun executableOf(command: String): File? {
        val program = programOf(command) ?: return null
        val file = (if (program.startsWith("/")) File(program) else onPath(program)) ?: return null
        val real = runCatching { file.toPath().toRealPath().toFile() }.getOrNull()?.takeIf { it.isFile } ?: return null
        return scriptTarget(real) ?: real
    }

    fun scriptTarget(script: File): File? {
        val head = runCatching { script.inputStream().use { it.readNBytes(SCRIPT_READ_LIMIT) } }.getOrNull() ?: return null
        val text = String(head)
        if (!text.startsWith(SCRIPT_MARK)) return null
        val interpreter = text.lineSequence().first().removePrefix(SCRIPT_MARK).trim().substringBefore(' ')
        return text.lineSequence().drop(1)
            .filterNot { it.trimStart().startsWith("#") }
            .flatMap { line -> SCRIPT_PATH.findAll(line).map { it.value } }
            .map(::File)
            .filter { it.path != interpreter && it.isFile && it.canExecute() && !isScript(it) }
            .firstOrNull()
    }

    private fun isScript(file: File): Boolean =
        runCatching { file.inputStream().use { String(it.readNBytes(2)) == SCRIPT_MARK } }.getOrDefault(false)

    private fun onPath(program: String): File? =
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
            .map { File(it, program) }
            .firstOrNull { it.isFile && it.canExecute() }

    private fun tokens(command: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        command.forEach { char ->
            when {
                escaped -> { current.append(char); escaped = false }
                char == '\\' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '"' || char == '\'') -> quote = char
                quote == null && char.isWhitespace() -> {
                    if (current.isNotEmpty()) result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun applicationDirs(): List<File> {
        val home = System.getProperty("user.home")
        val dataHome = System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: "$home/.local/share"
        val dataDirs = System.getenv("XDG_DATA_DIRS")?.ifBlank { null } ?: "/usr/local/share:/usr/share"
        return (listOf(dataHome) + dataDirs.split(':')).filter { it.isNotBlank() }.distinct().map { File(it, "applications") }
    }
}

object FlatpakApps {
    private const val APPLICATION_GROUP = "[Application]"
    private const val NAME_KEY = "name="
    private val SCOPE = Regex("(?m)app-flatpak-(.+)-\\d+\\.scope$")

    fun of(pid: Long): String? {
        val info = runCatching { File("/proc/$pid/root/.flatpak-info").readText() }.getOrDefault("")
        val cgroup = runCatching { File("/proc/$pid/cgroup").readText() }.getOrDefault("")
        return appOf(info, cgroup)
    }

    fun appOf(info: String, cgroup: String): String? {
        var inApplication = false
        info.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("[")) inApplication = line == APPLICATION_GROUP
            else if (inApplication && line.startsWith(NAME_KEY)) return line.removePrefix(NAME_KEY).trim().ifEmpty { null }
        }
        return SCOPE.find(cgroup)?.groupValues?.get(1)
    }
}

class IconThemes {
    private val home = System.getProperty("user.home")
    private val roots: List<String> = listOf("$home/.local/share/icons", "$home/.icons") +
        (System.getenv("XDG_DATA_DIRS")?.ifBlank { null } ?: "/usr/local/share:/usr/share").split(':').map { "$it/icons" }
    private val pixmaps = File("/usr/share/pixmaps")
    private val themes: List<String> by lazy { themeChain(currentTheme()) }
    private val index: Map<String, List<File>> by lazy { buildIndex() }

    fun find(icon: String): File? {
        File(icon).takeIf { it.isAbsolute && it.isFile }?.let { return it }
        val candidates = index[icon].orEmpty()
        return themes.firstNotNullOfOrNull { theme -> best(candidates.filter { "/icons/$theme/" in it.path }) }
            ?: best(candidates)
    }

    private fun best(files: List<File>): File? =
        files.maxWithOrNull(compareBy<File> { it.extension == "svg" }.thenBy { sizeOf(it) })

    private fun sizeOf(file: File): Int =
        Regex("/(\\d+)(x\\d+)?(@\\d+x?)?/").findAll(file.path).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.coerceAtMost(256) ?: 0

    private fun buildIndex(): Map<String, List<File>> {
        val themed = (themes.flatMap { theme -> roots.map { File(it, theme) } } + pixmaps).filter { it.isDirectory }.distinct()
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in IMAGE_TYPES }.toList() }
        val loose = roots.map(::File).flatMap { dir -> dir.listFiles { file -> file.isFile && file.extension in IMAGE_TYPES }.orEmpty().toList() }
        return (themed + loose).groupBy { it.nameWithoutExtension }
    }

    private fun currentTheme(): String? {
        val kde = File(home, ".config/kdeglobals").takeIf { it.isFile }?.let { themeIn(it.readText(), "[Icons]", "Theme") }
        val gtk = File(home, ".config/gtk-3.0/settings.ini").takeIf { it.isFile }?.let { themeIn(it.readText(), "[Settings]", "gtk-icon-theme-name") }
        return kde ?: gtk
    }

    private fun themeIn(text: String, group: String, key: String): String? {
        var inGroup = false
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("[")) inGroup = line == group
            else if (inGroup && line.substringBefore('=').trim() == key) return line.substringAfter('=').trim().ifBlank { null }
        }
        return null
    }

    private fun themeChain(theme: String?): List<String> {
        val chain = mutableListOf<String>()
        fun visit(name: String) {
            if (name in chain || chain.size > 8) return
            chain += name
            roots.map { File(it, "$name/index.theme") }.firstOrNull { it.isFile }
                ?.let { themeIn(it.readText(), "[Icon Theme]", "Inherits") }
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach(::visit)
        }
        theme?.let(::visit)
        visit(HICOLOR)
        return chain
    }

    private companion object {
        const val HICOLOR = "hicolor"
        val IMAGE_TYPES = setOf("svg", "png")
    }
}
