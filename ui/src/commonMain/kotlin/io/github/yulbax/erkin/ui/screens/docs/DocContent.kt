package io.github.yulbax.erkin.ui.screens.docs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.RocketLaunch
import io.github.yulbax.erkin.ui.components.IconSource
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.ic_byedpi

enum class DocArticle(val slug: String, val icon: IconSource) {
    START("start", IconSource.Vector(Icons.Filled.RocketLaunch)),
    CHANNELS("channels", IconSource.Vector(Icons.AutoMirrored.Filled.AltRoute)),
    BYEDPI("byedpi", IconSource.Drawable(Res.drawable.ic_byedpi)),
    TROUBLESHOOTING("troubleshooting", IconSource.Vector(Icons.Filled.Build));

    companion object {
        fun fromSlug(slug: String?): DocArticle? = entries.firstOrNull { it.slug == slug }
    }
}

data class Doc(val title: String, val blocks: List<DocBlock>)

sealed interface DocBlock {
    data class Text(val markdown: String) : DocBlock
    data class Image(val path: String, val description: String) : DocBlock
}

object DocParser {
    private const val TITLE_PREFIX = "# "
    private val IMAGE = Regex("""!\[([^\]]*)]\(([^)\s]+)\)""")

    fun parse(markdown: String, directory: String): Doc {
        val lines = markdown.lines()
        val titleLine = lines.indexOfFirst { it.startsWith(TITLE_PREFIX) }
        val title = lines.getOrNull(titleLine)?.removePrefix(TITLE_PREFIX)?.trim().orEmpty()
        val blocks = mutableListOf<DocBlock>()
        val text = StringBuilder()
        fun flush() {
            text.toString().trim().takeIf { it.isNotEmpty() }?.let { blocks += DocBlock.Text(it) }
            text.clear()
        }
        lines.drop(titleLine + 1).forEach { line ->
            val image = IMAGE.matchEntire(line.trim())
            if (image == null) {
                text.appendLine(line)
            } else {
                flush()
                blocks += DocBlock.Image(resolve(directory, image.groupValues[2]), image.groupValues[1])
            }
        }
        flush()
        return Doc(title, blocks)
    }

    fun resolve(directory: String, relative: String): String {
        val parts = directory.split('/').filter { it.isNotEmpty() }.toMutableList()
        relative.split('/').forEach { part ->
            when (part) {
                ".." -> parts.removeLastOrNull()
                ".", "" -> Unit
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }
}

object DocLibrary {
    private const val ROOT = "files/docs"
    const val FALLBACK_LANGUAGE = "en"

    suspend fun load(article: DocArticle, language: String): Doc {
        val (directory, bytes) = listOf(language, FALLBACK_LANGUAGE).distinct().firstNotNullOf { candidate ->
            val directory = "$ROOT/$candidate"
            runCatching { directory to Res.readBytes("$directory/${article.slug}.md") }.getOrNull()
        }
        return DocParser.parse(bytes.decodeToString(), directory)
    }
}
