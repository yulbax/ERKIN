package io.github.yulbax.erkin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.yulbax.erkin.ui.platform.platformTextStyle
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.Font

private val base = Typography()

@Composable
fun erkinTypography(): Typography {
    val googleSans = FontFamily(
        Font(Res.font.google_sans_regular, FontWeight.Normal),
        Font(Res.font.google_sans_medium, FontWeight.Medium),
        Font(Res.font.google_sans_bold, FontWeight.Bold)
    )
    return Typography(
        displayLarge = base.displayLarge.erkin(googleSans),
        displayMedium = base.displayMedium.erkin(googleSans),
        displaySmall = base.displaySmall.erkin(googleSans),
        headlineLarge = base.headlineLarge.erkin(googleSans),
        headlineMedium = base.headlineMedium.erkin(googleSans),
        headlineSmall = base.headlineSmall.erkin(googleSans),
        titleLarge = base.titleLarge.erkin(googleSans),
        titleMedium = base.titleMedium.erkin(googleSans),
        titleSmall = base.titleSmall.erkin(googleSans),
        bodyLarge = base.bodyLarge.erkin(googleSans),
        bodyMedium = base.bodyMedium.erkin(googleSans),
        bodySmall = base.bodySmall.erkin(googleSans),
        labelLarge = base.labelLarge.erkin(googleSans),
        labelMedium = base.labelMedium.erkin(googleSans),
        labelSmall = base.labelSmall.erkin(googleSans)
    )
}

private fun TextStyle.erkin(fontFamily: FontFamily): TextStyle {
    val styled = copy(fontFamily = fontFamily)
    return platformTextStyle?.let { styled.copy(platformStyle = it) } ?: styled
}
