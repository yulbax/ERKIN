package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yulbax.erkin.ui.platform.flagFontFamily
import java.util.Locale

@Composable
fun CountryFlag(country: String, fontSize: TextUnit = 22.sp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 36.dp, height = 26.dp)
            .clearAndSetSemantics { contentDescription = country }
    ) {
        Text(text = country.toFlagEmoji(), fontSize = fontSize, lineHeight = fontSize, fontFamily = flagFontFamily())
    }
}

private fun String.toFlagEmoji(): String {
    val code = trim().uppercase(Locale.ROOT)
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return code
    return buildString {
        code.forEach { letter -> append(String(Character.toChars(0x1F1E6 + letter.code - 'A'.code))) }
    }
}
