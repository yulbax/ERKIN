package io.github.yulbax.erkin.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.Window

object WindowsDwm {

    @Suppress("FunctionName")
    private interface Api : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2

    fun roundCorners(window: Window) {
        if (!DesktopPaths.isWindows) return
        runCatching {
            val api = Native.load("dwmapi", Api::class.java)
            api.DwmSetWindowAttribute(Native.getWindowPointer(window), DWMWA_WINDOW_CORNER_PREFERENCE, IntByReference(DWMWCP_ROUND), Int.SIZE_BYTES)
        }
    }
}
