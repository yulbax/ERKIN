package io.github.yulbax.erkin.ui.platform

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

object BundledResources {
    val dir: File = System.getProperty("compose.application.resources.dir")?.let(::File)
        ?: System.getenv("ERKIN_BIN_DIR")?.let(::File)
        ?: File("binaries")

    fun fontFamily(vararg fonts: Pair<String, FontWeight>): FontFamily? =
        fonts.map { (name, weight) ->
            val file = File(dir, "fonts/$name").takeIf { it.isFile } ?: return null
            Font(file, weight)
        }.let(::FontFamily)
}
