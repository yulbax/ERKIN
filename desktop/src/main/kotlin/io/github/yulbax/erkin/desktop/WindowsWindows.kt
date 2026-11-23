package io.github.yulbax.erkin.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

object WindowsWindows {

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun EnumWindows(callback: EnumWindowsProc, data: Pointer?): Boolean
        fun IsWindowVisible(hwnd: Pointer): Boolean
        fun GetWindow(hwnd: Pointer, command: Int): Pointer?
        fun GetWindowTextLengthW(hwnd: Pointer): Int
        fun GetWindowThreadProcessId(hwnd: Pointer, processId: IntByReference): Int
    }

    private fun interface EnumWindowsProc : Callback {
        fun invoke(hwnd: Pointer, data: Pointer?): Boolean
    }

    private const val GW_OWNER = 4

    private val api: User32? by lazy {
        if (DesktopPaths.isWindows) runCatching { Native.load("user32", User32::class.java) }.getOrNull() else null
    }

    fun withWindows(): Set<Long> {
        val user32 = api ?: return emptySet()
        val owners = HashSet<Long>()
        runCatching {
            user32.EnumWindows({ hwnd, _ ->
                if (user32.IsWindowVisible(hwnd) && user32.GetWindow(hwnd, GW_OWNER) == null && user32.GetWindowTextLengthW(hwnd) > 0) {
                    val processId = IntByReference()
                    user32.GetWindowThreadProcessId(hwnd, processId)
                    owners += processId.value.toLong()
                }
                true
            }, null)
        }
        return owners
    }
}
