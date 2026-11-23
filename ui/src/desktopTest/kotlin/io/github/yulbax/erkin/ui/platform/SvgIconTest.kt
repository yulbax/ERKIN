package io.github.yulbax.erkin.ui.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Test
import org.junit.Assert.assertEquals

class SvgIconTest {
    private fun square(declared: String) =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" $declared><rect width=\"100%\" height=\"100%\" fill=\"#ff0000\"/></svg>".toByteArray()

    private fun corners(bytes: ByteArray): List<Color> {
        val pixels = renderSvg(bytes, 64).toPixelMap()
        return listOf(pixels[1, 1], pixels[62, 62], pixels[32, 32])
    }

    @Test
    fun anIconOfAnyDeclaredSizeFillsItsTile() {
        listOf(
            "width=\"512\" height=\"512\"",
            "width=\"16\" height=\"16\"",
            "width=\"128px\" height=\"128px\" viewBox=\"0 0 128 128\"",
            "viewBox=\"0 0 48 48\""
        ).forEach { declared ->
            assertEquals(declared, List(3) { Color.Red }, corners(square(declared)))
        }
    }
}
