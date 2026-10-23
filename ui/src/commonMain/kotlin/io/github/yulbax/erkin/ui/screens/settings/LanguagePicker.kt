package io.github.yulbax.erkin.ui.screens.settings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.components.DropdownSetting
import io.github.yulbax.erkin.ui.platform.AppLocale

private enum class AppLanguage(val tag: String, val displayName: String) {
    English("en", "English"),
    Russian("ru", "Русский"),
    Ukrainian("uk", "Українська"),
    Chinese("zh", "中文"),
    Persian("fa", "فارسی");

    companion object {
        fun fromTag(tag: String): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

@Composable
internal fun LanguagePicker() {
    val systemLabel = stringResource(Res.string.language_system)
    val currentTag = AppLocale.currentTag() ?: ""

    DropdownSetting(
        label = stringResource(Res.string.language),
        options = listOf<AppLanguage?>(null) + AppLanguage.entries,
        selected = AppLanguage.fromTag(currentTag),
        optionLabel = { it?.displayName ?: systemLabel },
        onSelect = { language -> AppLocale.apply(language?.tag) }
    )
}
