package io.github.yulbax.erkin.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.yulbax.erkin.ui.theme.StatusAmber
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.vpn.core.ByeDpiQuality

@Composable
fun ByeDpiQuality.color(): Color = when (this) {
    ByeDpiQuality.GOOD -> StatusGreen
    ByeDpiQuality.FAIR -> StatusAmber
    ByeDpiQuality.POOR -> MaterialTheme.colorScheme.error
}
