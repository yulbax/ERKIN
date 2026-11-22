package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun LabeledFrame(label: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    var notch by remember { mutableStateOf(IntSize.Zero) }
    val half = with(LocalDensity.current) { (notch.height / 2).toDp() }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = half)
                .drawBehind {
                    val stroke = STROKE.toPx()
                    val start = LABEL_START.toPx()
                    clipRect(left = start, top = -stroke, right = start + notch.width, bottom = stroke * 2, clipOp = ClipOp.Difference) {
                        drawRoundRect(
                            color = outline,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(CORNER.toPx()),
                            style = Stroke(stroke)
                        )
                    }
                }
                .clip(RoundedCornerShape(CORNER))
        ) {
            Box(Modifier.fillMaxSize().padding(top = half).clipToBounds(), content = content)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(start = LABEL_START)
                .onSizeChanged { notch = it }
                .padding(horizontal = LABEL_GAP)
        )
    }
}

private val CORNER = 24.dp
private val STROKE = 1.dp
private val LABEL_START = 18.dp
private val LABEL_GAP = 6.dp
