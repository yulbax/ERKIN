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
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.platform.flagFontFamily
import io.github.yulbax.erkin.ui.res.*

private enum class LicenseText(val title: String, val path: String) {
    GPL("GNU General Public License v3.0", "files/gpl_3_0.txt"),
    MIT("MIT License", "files/byedpi_mit.txt"),
    OFL("SIL Open Font License 1.1", "files/google_sans_ofl.txt")
}

@Composable
fun License() {
    val texts by produceState(emptyMap<LicenseText, String>()) {
        value = LicenseText.entries.associateWith { readFile(it.path) }
    }
    val notices = listOfNotNull(
        Res.string.about_core to Res.string.license_core_notice,
        Res.string.license_strategies_title to Res.string.license_strategies_notice,
        Res.string.about_byedpi to Res.string.license_byedpi_notice,
        Res.string.license_font_title to Res.string.license_font_notice,
        (Res.string.license_flags_title to Res.string.license_flags_notice).takeIf { flagFontFamily() != null }
    )

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

        GroupCard(
            items = notices.map { (title, notice) ->
                { NoticeRow(stringResource(title), stringResource(notice)) }
            }
        )

        LicenseText.entries.forEach { license ->
            LicenseSection(license.title, texts[license].orEmpty())
        }
    }
}

@Composable
private fun NoticeRow(title: String, notice: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = notice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun LicenseSection(title: String, text: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
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
}

private suspend fun readFile(path: String): String =
    runCatching { Res.readBytes(path).decodeToString() }.getOrDefault("")
