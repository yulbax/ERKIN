package io.github.yulbax.erkin.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    data class Restored(val size: Dimension, val position: Point?)

    fun encode(): String = "$x $y $width $height"

    fun restoredOn(screens: List<Rectangle>): Restored {
        val minimum = WindowLimits.minimum
        val tallest = screens.maxOfOrNull { it.height } ?: Int.MAX_VALUE
        val size = Dimension(
            width.coerceIn(minimum.width, WindowLimits.MAX_WIDTH),
            height.coerceIn(minimum.height, maxOf(minimum.height, tallest))
        )
        val grip = Rectangle(x, y, size.width, GRIP_HEIGHT)
        val reachable = screens.any { screen ->
            val overlap = screen.intersection(grip)
            !overlap.isEmpty && overlap.width >= MIN_GRIP_WIDTH && overlap.height >= MIN_GRIP_HEIGHT
        }
        return Restored(size, if (reachable) Point(x, y) else null)
    }

    companion object {
        private const val GRIP_HEIGHT = 48
        private const val MIN_GRIP_WIDTH = 120
        private const val MIN_GRIP_HEIGHT = 24

        fun of(rectangle: Rectangle) = WindowBounds(rectangle.x, rectangle.y, rectangle.width, rectangle.height)

        fun decode(text: String): WindowBounds? =
            text.trim().split(' ').map { it.toIntOrNull() ?: return null }
                .takeIf { it.size == 4 }
                ?.let { (x, y, width, height) -> WindowBounds(x, y, width, height) }
    }
}

object WindowMemory {
    private val DEFAULT_SIZE = DpSize(440.dp, 860.dp)
    private const val SAVE_DELAY_MS = 500L

    private val file: File = DesktopPaths.windowFile

    fun initialState(): WindowState {
        val restored = load()?.restoredOn(screens())
        return WindowState(
            position = restored?.position?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = restored?.size?.let { DpSize(it.width.dp, it.height.dp) } ?: DEFAULT_SIZE
        )
    }

    @Composable
    fun FrameWindowScope.Remember(state: WindowState) {
        LaunchedEffect(state) {
            snapshotFlow { state.position to state.size }.collectLatest {
                delay(SAVE_DELAY_MS)
                if (window.isShowing && !state.isMinimized) save(WindowBounds.of(window.bounds))
            }
        }
    }

    private fun load(): WindowBounds? = runCatching { WindowBounds.decode(file.readText()) }.getOrNull()

    private fun save(bounds: WindowBounds) {
        runCatching { file.writeText(bounds.encode()) }
    }

    private fun screens(): List<Rectangle> = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    }.getOrDefault(emptyList())
}
