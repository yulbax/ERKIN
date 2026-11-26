package io.github.yulbax.erkin.desktop

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowBoundsTest {
    private val left = Rectangle(0, 0, 1920, 1080)
    private val right = Rectangle(1920, 0, 2560, 1440)

    @Test
    fun boundsSurviveTheirText() {
        val bounds = WindowBounds(-1200, 40, 480, 900)
        assertEquals(bounds, WindowBounds.decode(bounds.encode()))
    }

    @Test
    fun brokenTextIsIgnored() {
        assertNull(WindowBounds.decode(""))
        assertNull(WindowBounds.decode("10 20 480"))
        assertNull(WindowBounds.decode("10 20 wide 900"))
    }

    @Test
    fun aWindowOnAnyScreenKeepsItsPlace() {
        val restored = WindowBounds(2400, 200, 480, 900).restoredOn(listOf(left, right))
        assertEquals(Point(2400, 200), restored.position)
        assertEquals(Dimension(480, 900), restored.size)
    }

    @Test
    fun aWindowOnAnUnpluggedScreenLetsTheSystemPlaceIt() {
        val restored = WindowBounds(2400, 200, 480, 900).restoredOn(listOf(left))
        assertNull(restored.position)
        assertEquals(Dimension(480, 900), restored.size)
    }

    @Test
    fun aWindowWithItsTopBarOffScreenLetsTheSystemPlaceIt() {
        assertNull(WindowBounds(100, -300, 480, 900).restoredOn(listOf(left)).position)
        assertNull(WindowBounds(1850, 100, 480, 900).restoredOn(listOf(left)).position)
    }

    @Test
    fun theSizeStaysWithinTheWindowLimitsAndTheTallestScreen() {
        assertEquals(Dimension(WindowLimits.MAX_WIDTH, 1080), WindowBounds(0, 0, 2000, 3000).restoredOn(listOf(left)).size)
        assertEquals(WindowLimits.minimum, WindowBounds(0, 0, 10, 10).restoredOn(listOf(left)).size)
    }
}
