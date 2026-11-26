package io.github.yulbax.erkin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.ThemeMode
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

@Composable
fun rememberDarkTheme(): Boolean? {
    val settings = koinInject<SettingsRepository>()
    val mode: ThemeMode? by remember(settings) { settings.settings.map { it.themeMode() } }.collectAsState(null)
    val systemDark = isSystemInDarkTheme()
    return mode?.isDark(systemDark)
}
