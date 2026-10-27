package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.platform.flagFontFamily
import io.github.yulbax.erkin.ui.res.*

@Composable
fun License() {
    val gpl by produceState("") { value = readFile("files/gpl_3_0.txt") }
    val ofl by produceState("") { value = readFile("files/google_sans_ofl.txt") }
    val bundlesFlagFont = flagFontFamily() != null
    val flagsOfl by produceState("", bundlesFlagFont) {
        if (bundlesFlagFont) value = readFile("files/noto_emoji_ofl.txt")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(Res.string.license_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LicenseSection(
            title = stringResource(Res.string.license_app_title),
            text = gpl
        )

        LicenseSection(
            title = stringResource(Res.string.license_font_title),
            notice = stringResource(Res.string.license_font_notice),
            text = ofl
        )

        LicenseSection(
            title = stringResource(Res.string.license_strategies_title),
            notice = stringResource(Res.string.license_strategies_notice),
            text = gpl
        )

        if (bundlesFlagFont) {
            LicenseSection(
                title = stringResource(Res.string.license_flags_title),
                notice = stringResource(Res.string.license_flags_notice),
                text = flagsOfl
            )
        }
    }
}

@Composable
private fun LicenseSection(title: String, text: String, notice: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
    if (notice != null) {
        Text(
            text = notice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        SelectionContainer(modifier = Modifier.padding(12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private suspend fun readFile(path: String): String =
    runCatching { Res.readBytes(path).decodeToString() }.getOrDefault("")
