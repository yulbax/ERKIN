package io.github.yulbax.erkin.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Cursor
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

object WindowLimits {
    val minimum = Dimension(360, 560)
    const val MAX_WIDTH = 600

    fun clampWidth(window: Window) {
        window.maximumSize = Dimension(MAX_WIDTH, Int.MAX_VALUE)
        if (!DesktopPaths.isWindows) return
        window.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (window.width > MAX_WIDTH) window.setSize(MAX_WIDTH, window.height)
            }
        })
        if (window.width > MAX_WIDTH) window.setSize(MAX_WIDTH, window.height)
    }
}

@Composable
fun FrameWindowScope.WindowFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxSize().padding(1.dp)) { content() }
        ResizeHandles()
    }
}

@Composable
fun CaptionButtons(onMinimize: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CaptionButton(Icons.Filled.Remove, PastelYellow, onMinimize)
        CaptionButton(Icons.Filled.Close, PastelRed, onClose)
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (hovered) 0.36f else 0.22f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

private enum class Edge(val cursor: Int, val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    LEFT(Cursor.W_RESIZE_CURSOR, true, false, false, false),
    RIGHT(Cursor.E_RESIZE_CURSOR, false, false, true, false),
    TOP(Cursor.N_RESIZE_CURSOR, false, true, false, false),
    BOTTOM(Cursor.S_RESIZE_CURSOR, false, false, false, true),
    TOP_LEFT(Cursor.NW_RESIZE_CURSOR, true, true, false, false),
    TOP_RIGHT(Cursor.NE_RESIZE_CURSOR, false, true, true, false),
    BOTTOM_LEFT(Cursor.SW_RESIZE_CURSOR, true, false, false, true),
    BOTTOM_RIGHT(Cursor.SE_RESIZE_CURSOR, false, false, true, true);

    fun resize(start: Rectangle, dx: Int, dy: Int, maxHeight: Int): Rectangle {
        val minimum = WindowLimits.minimum
        var x = start.x
        var y = start.y
        var width = start.width
        var height = start.height
        if (right) width = (start.width + dx).coerceIn(minimum.width, WindowLimits.MAX_WIDTH)
        if (left) {
            width = (start.width - dx).coerceIn(minimum.width, WindowLimits.MAX_WIDTH)
            x = start.x + start.width - width
        }
        if (bottom) height = (start.height + dy).coerceIn(minimum.height, maxHeight)
        if (top) {
            height = (start.height - dy).coerceIn(minimum.height, maxHeight)
            y = start.y + start.height - height
        }
        return Rectangle(x, y, width, height)
    }
}

@Composable
private fun FrameWindowScope.ResizeHandles() {
    val thickness = 5.dp
    val corner = 10.dp
    Box(Modifier.fillMaxSize()) {
        ResizeHandle(Edge.LEFT, Modifier.align(Alignment.CenterStart).width(thickness).fillMaxHeight())
        ResizeHandle(Edge.RIGHT, Modifier.align(Alignment.CenterEnd).width(thickness).fillMaxHeight())
        ResizeHandle(Edge.TOP, Modifier.align(Alignment.TopCenter).height(thickness).fillMaxWidth())
        ResizeHandle(Edge.BOTTOM, Modifier.align(Alignment.BottomCenter).height(thickness).fillMaxWidth())
        ResizeHandle(Edge.TOP_LEFT, Modifier.align(Alignment.TopStart).size(corner))
        ResizeHandle(Edge.TOP_RIGHT, Modifier.align(Alignment.TopEnd).size(corner))
        ResizeHandle(Edge.BOTTOM_LEFT, Modifier.align(Alignment.BottomStart).size(corner))
        ResizeHandle(Edge.BOTTOM_RIGHT, Modifier.align(Alignment.BottomEnd).size(corner))
    }
}

@Composable
private fun FrameWindowScope.ResizeHandle(edge: Edge, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge) {
                var start = Rectangle()
                var origin = Point()
                detectDragGestures(
                    onDragStart = {
                        start = window.bounds
                        origin = MouseInfo.getPointerInfo().location
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pointer = MouseInfo.getPointerInfo().location
                        val maxHeight = window.graphicsConfiguration.bounds.height
                        window.bounds = edge.resize(start, pointer.x - origin.x, pointer.y - origin.y, maxHeight)
                    }
                )
            }
    )
}

private val PastelYellow = Color(0xFFFFE083)
private val PastelRed = Color(0xFFFF9C94)
