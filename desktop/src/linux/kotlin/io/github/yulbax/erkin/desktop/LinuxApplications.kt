package io.github.yulbax.erkin.desktop

import java.io.File
import java.nio.file.Files
import java.util.Locale

data class DesktopEntry(
    val name: String,
    val command: String,
    val icon: String?,
    val hidden: Boolean,
    val flatpak: String? = null
)

class HostSystem(
    val home: String,
    val dataHome: String,
    val dataDirs: List<String>,
    val path: List<String>,
    private val visible: (String) -> String,
    private val hidden: (String) -> Boolean = { false }
) {
    fun file(hostPath: String): File = File(visible(hostPath))

    fun isHidden(hostPath: String): Boolean = hidden(hostPath)

    fun isProgram(hostPath: String): Boolean = realPath(hostPath)?.let(::file)?.let { it.isFile && it.canExecute() } == true

    fun realPath(hostPath: String): String? {
        val pending = ArrayDeque(components(hostPath))
        var resolved = ""
        var links = 0
        while (pending.isNotEmpty()) {
            val part = pending.removeFirst()
            if (part == ".") continue
            if (part == "..") {
                resolved = resolved.substringBeforeLast('/', "")
                continue
            }
            val candidate = "$resolved/$part"
            val shown = file(candidate).toPath()
            if (!Files.isSymbolicLink(shown)) {
                resolved = candidate
                continue
            }
            if (++links > MAX_LINKS) return null
            val target = Files.readSymbolicLink(shown).toString()
            if (target.startsWith("/")) resolved = ""
            components(target).asReversed().forEach(pending::addFirst)
        }
        return resolved.ifEmpty { "/" }.takeIf { file(it).exists() }
    }

    private fun components(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    companion object {
        private const val MAX_LINKS = 40
        private const val SANDBOX_HOST_ROOT = "/run/host"
        private val HOST_OS_DIRS = listOf("/usr", "/etc", "/bin", "/sbin", "/lib", "/lib32", "/lib64")
        private val DEFAULT_DATA_DIRS = listOf("/usr/local/share", "/usr/share")
        private val DEFAULT_PATH = listOf("/usr/local/sbin", "/usr/local/bin", "/usr/bin", "/usr/sbin", "/bin", "/sbin")
        private const val SYSTEM_FLATPAK_EXPORTS = "/var/lib/flatpak/exports/share"

        fun current(): HostSystem {
            val home = System.getProperty("user.home")
            val dataHome = "$home/.local/share"
            if (FlatpakSandbox.isActive) {
                return HostSystem(
                    home = home,
                    dataHome = dataHome,
                    dataDirs = listOf("$dataHome/flatpak/exports/share", SYSTEM_FLATPAK_EXPORTS) + DEFAULT_DATA_DIRS,
                    path = DEFAULT_PATH,
                    visible = { path -> if (HOST_OS_DIRS.any { path == it || path.startsWith("$it/") }) SANDBOX_HOST_ROOT + path else path },
                    hidden = { path -> path.startsWith("$home/") && !File(path).exists() }
                )
            }
            return HostSystem(
                home = home,
                dataHome = System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: dataHome,
                dataDirs = System.getenv("XDG_DATA_DIRS")?.ifBlank { null }?.split(':') ?: DEFAULT_DATA_DIRS,
                path = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar),
                visible = { it }
            )
        }
    }
}

object LinuxApplications {
    private const val ENTRY_GROUP = "[Desktop Entry]"
    private const val APPLICATION = "Application"
    private val FIELD_CODE = Regex("%[a-zA-Z%]")
    private val ENVIRONMENT_ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")
    private val SKIPPED_LAUNCHERS = setOf("env", "flatpak", "snap")
    private const val SCRIPT_MARK = "#!"
    private const val SCRIPT_READ_LIMIT = 64 * 1024
    private val SCRIPT_PATH = Regex("(/[A-Za-z0-9._+@/-]+)")

    fun list(host: HostSystem = HostSystem.current()): List<CatalogProgram> {
        val icons = IconThemes(host)
        return applicationDirs(host)
            .flatMap { dir -> host.file(dir).listFiles { file -> file.extension == "desktop" }.orEmpty().sortedBy { it.name } }
            .distinctBy { it.name }
            .mapNotNull { file -> runCatching { parse(file.readText()) }.getOrNull() }
            .filterNot { it.hidden }
            .mapNotNull { entry ->
                val icon = entry.icon?.let(icons::find)
                entry.flatpak?.let { CatalogProgram.flatpak(entry.name, it, icon) }
                    ?: executableOf(entry.command, host)?.let { CatalogProgram.executable(entry.name, File(it), icon) }
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

    private fun executableOf(command: String, host: HostSystem): String? {
        val program = programOf(command) ?: return null
        val located = if (program.startsWith("/")) program else host.path.map { "$it/$program" }.firstOrNull(host::isProgram)
        val real = located?.let(host::realPath)?.takeIf { host.file(it).isFile }
            ?: return located?.takeIf { it.startsWith("/") && host.isHidden(it) }
        return scriptTarget(real, host) ?: real
    }

    fun scriptTarget(script: String, host: HostSystem): String? {
        val head = runCatching { host.file(script).inputStream().use { it.readNBytes(SCRIPT_READ_LIMIT) } }.getOrNull() ?: return null
        val text = String(head)
        if (!text.startsWith(SCRIPT_MARK)) return null
        val interpreter = text.lineSequence().first().removePrefix(SCRIPT_MARK).trim().substringBefore(' ')
        return text.lineSequence().drop(1)
            .filterNot { it.trimStart().startsWith("#") }
            .flatMap { line -> SCRIPT_PATH.findAll(line).map { it.value } }
            .filter { it != interpreter && host.isProgram(it) && !isScript(host.file(checkNotNull(host.realPath(it)))) }
            .firstOrNull()
    }

    private fun isScript(file: File): Boolean =
        runCatching { file.inputStream().use { String(it.readNBytes(2)) == SCRIPT_MARK } }.getOrDefault(false)

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

    private fun applicationDirs(host: HostSystem): List<String> =
        (listOf(host.dataHome) + host.dataDirs).filter { it.isNotBlank() }.distinct().map { "$it/applications" }
}

object IniFiles {
    fun value(text: String, group: String, key: String): String? {
        var inGroup = false
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("[")) inGroup = line == group
            else if (inGroup && line.substringBefore('=').trim() == key) return line.substringAfter('=').trim().ifBlank { null }
        }
        return null
    }
}

class IconThemes(private val host: HostSystem) {
    private val roots: List<String> = listOf("${host.home}/.local/share/icons", "${host.home}/.icons") + host.dataDirs.map { "$it/icons" }
    private val themes: List<String> by lazy { themeChain(currentTheme()) }
    private val index: Map<String, List<File>> by lazy { buildIndex() }

    fun find(icon: String): File? {
        if (icon.startsWith("/")) return host.file(icon).takeIf { it.isFile }
        val candidates = index[icon].orEmpty()
        return themes.firstNotNullOfOrNull { theme -> best(candidates.filter { "/icons/$theme/" in it.path }) }
            ?: best(candidates)
    }

    private fun best(files: List<File>): File? =
        files.maxWithOrNull(compareBy<File> { it.extension == "svg" }.thenBy { sizeOf(it) })

    private fun sizeOf(file: File): Int =
        Regex("/(\\d+)(x\\d+)?(@\\d+x?)?/").findAll(file.path).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.coerceAtMost(256) ?: 0

    private fun buildIndex(): Map<String, List<File>> {
        val themed = (themes.flatMap { theme -> roots.map { host.file("$it/$theme") } } + host.file(PIXMAPS)).filter { it.isDirectory }.distinct()
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in IMAGE_TYPES }.toList() }
        val loose = roots.map(host::file).flatMap { dir -> dir.listFiles { file -> file.isFile && file.extension in IMAGE_TYPES }.orEmpty().toList() }
        return (themed + loose).groupBy { it.nameWithoutExtension }
    }

    private fun currentTheme(): String? {
        val kde = host.file("${host.home}/.config/kdeglobals").takeIf { it.isFile }?.let { IniFiles.value(it.readText(), "[Icons]", "Theme") }
        val gtk = host.file("${host.home}/.config/gtk-3.0/settings.ini").takeIf { it.isFile }
            ?.let { IniFiles.value(it.readText(), "[Settings]", "gtk-icon-theme-name") }
        return kde ?: gtk
    }

    private fun themeChain(theme: String?): List<String> {
        val chain = mutableListOf<String>()
        fun visit(name: String) {
            if (name in chain || chain.size > 8) return
            chain += name
            roots.map { host.file("$it/$name/index.theme") }.firstOrNull { it.isFile }
                ?.let { IniFiles.value(it.readText(), "[Icon Theme]", "Inherits") }
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach(::visit)
        }
        theme?.let(::visit)
        visit(HICOLOR)
        return chain
    }

    private companion object {
        const val HICOLOR = "hicolor"
        const val PIXMAPS = "/usr/share/pixmaps"
        val IMAGE_TYPES = setOf("svg", "png")
    }
}
