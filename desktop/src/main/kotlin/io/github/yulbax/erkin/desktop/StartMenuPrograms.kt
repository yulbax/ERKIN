package io.github.yulbax.erkin.desktop

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class StartMenuProgram(val name: String, val executable: File)

object StartMenuPrograms {
    private const val SHORTCUT_EXTENSION = "lnk"
    private const val PROGRAM_EXTENSION = ".exe"
    private const val START_MENU = "Microsoft/Windows/Start Menu/Programs"
    private val UNINSTALLER = Regex("(?i)uninstall|^unins\\d*$")

    fun list(): List<StartMenuProgram> {
        val roots = listOfNotNull(System.getenv("ProgramData"), System.getenv("APPDATA")).map { File(it, START_MENU) }
        val systemRoot = System.getenv("SystemRoot")
        return programs(
            shortcuts = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension.equals(SHORTCUT_EXTENSION, ignoreCase = true) } },
            target = { file -> runCatching { ShellLink.target(file.readBytes(), System::getenv) }.getOrNull() },
            excludedRoots = listOfNotNull(systemRoot)
        )
    }

    fun programs(shortcuts: List<File>, target: (File) -> String?, excludedRoots: List<String>): List<StartMenuProgram> =
        shortcuts
            .mapNotNull { shortcut -> target(shortcut)?.let { StartMenuProgram(shortcut.nameWithoutExtension, File(it)) } }
            .filter { it.executable.name.endsWith(PROGRAM_EXTENSION, ignoreCase = true) && it.executable.isFile }
            .filterNot { program -> excludedRoots.any { program.executable.path.startsWith(it, ignoreCase = true) } }
            .filterNot { UNINSTALLER.containsMatchIn(it.name) || UNINSTALLER.containsMatchIn(it.executable.nameWithoutExtension) }
            .groupBy { it.executable.path.lowercase() }
            .values
            .map { same -> same.minBy { it.name.length } }
}

object ShellLink {
    private const val HEADER_SIZE = 0x4C
    private const val LINK_FLAGS_OFFSET = 0x14
    private const val HAS_TARGET_ID_LIST = 0x1
    private const val HAS_LINK_INFO = 0x2
    private const val IS_UNICODE = 0x80
    private val STRING_DATA_FLAGS = listOf(0x4, 0x8, 0x10, 0x20, 0x40)
    private const val VOLUME_ID_AND_LOCAL_BASE_PATH = 0x1
    private const val UNICODE_OFFSETS_HEADER_SIZE = 0x24
    private const val ENVIRONMENT_BLOCK = 0xA0000001.toInt()
    private const val ENVIRONMENT_UNICODE_OFFSET = 8 + 260
    private val VARIABLE = Regex("%([^%]+)%")

    fun target(bytes: ByteArray, environment: (String) -> String?): String? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < HEADER_SIZE || buffer.getInt(0) != HEADER_SIZE) return null
        val flags = buffer.getInt(LINK_FLAGS_OFFSET)
        var position = HEADER_SIZE
        if (flags and HAS_TARGET_ID_LIST != 0) position += 2 + (buffer.getShort(position).toInt() and 0xFFFF)
        var linkInfoPath: String? = null
        if (flags and HAS_LINK_INFO != 0) {
            linkInfoPath = localPath(buffer, position)
            position += buffer.getInt(position)
        }
        linkInfoPath?.let { return it }
        val unicode = flags and IS_UNICODE != 0
        STRING_DATA_FLAGS.filter { flags and it != 0 }.forEach {
            val characters = buffer.getShort(position).toInt() and 0xFFFF
            position += 2 + characters * (if (unicode) 2 else 1)
        }
        return environmentTarget(buffer, position)?.let { expand(it, environment) }
    }

    private fun localPath(buffer: ByteBuffer, start: Int): String? {
        val headerSize = buffer.getInt(start + 4)
        if (buffer.getInt(start + 8) and VOLUME_ID_AND_LOCAL_BASE_PATH == 0) return null
        if (headerSize >= UNICODE_OFFSETS_HEADER_SIZE) {
            val base = buffer.getInt(start + 28).takeIf { it != 0 }?.let { unicodeString(buffer, start + it) }
            val suffix = buffer.getInt(start + 32).takeIf { it != 0 }?.let { unicodeString(buffer, start + it) }.orEmpty()
            if (base != null) return base + suffix
        }
        val base = ansiString(buffer, start + buffer.getInt(start + 16))
        val suffix = buffer.getInt(start + 24).takeIf { it != 0 }?.let { ansiString(buffer, start + it) }.orEmpty()
        return base + suffix
    }

    private fun environmentTarget(buffer: ByteBuffer, start: Int): String? {
        var position = start
        while (position + 8 <= buffer.limit()) {
            val size = buffer.getInt(position)
            if (size < 8) return null
            if (buffer.getInt(position + 4) == ENVIRONMENT_BLOCK) {
                return unicodeString(buffer, position + ENVIRONMENT_UNICODE_OFFSET).ifEmpty {
                    ansiString(buffer, position + 8)
                }.ifEmpty { null }
            }
            position += size
        }
        return null
    }

    private fun expand(path: String, environment: (String) -> String?): String =
        VARIABLE.replace(path) { match -> environment(match.groupValues[1]) ?: match.value }

    private fun unicodeString(buffer: ByteBuffer, start: Int): String {
        val builder = StringBuilder()
        var position = start
        while (position + 1 < buffer.limit()) {
            val character = buffer.getChar(position)
            if (character == '\u0000') break
            builder.append(character)
            position += 2
        }
        return builder.toString()
    }

    private fun ansiString(buffer: ByteBuffer, start: Int): String {
        var end = start
        while (end < buffer.limit() && buffer.get(end) != 0.toByte()) end++
        return String(buffer.array(), start, end - start, ansiCharset)
    }

    private val ansiCharset: Charset =
        runCatching { Charset.forName(System.getProperty("native.encoding")) }.getOrDefault(Charsets.ISO_8859_1)
}
