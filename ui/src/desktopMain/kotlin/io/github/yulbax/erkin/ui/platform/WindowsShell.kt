package io.github.yulbax.erkin.ui.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString

internal object WindowsShell {

    @Suppress("FunctionName")
    private interface Shell32 : Library {
        fun ILCreateFromPathW(path: WString): Pointer?
        fun ILFree(pidl: Pointer)
        fun SHOpenFolderAndSelectItems(pidlFolder: Pointer, cidl: Int, apidl: Pointer?, flags: Int): Int
    }

    @Suppress("FunctionName")
    private interface Ole32 : Library {
        fun CoInitializeEx(reserved: Pointer?, coInit: Int): Int
        fun CoUninitialize()
    }

    private const val COINIT_APARTMENTTHREADED = 2

    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun revealInExplorer(path: String) {
        Thread({
            runCatching {
                val ole32 = Native.load("ole32", Ole32::class.java)
                val shell32 = Native.load("shell32", Shell32::class.java)
                val initialized = ole32.CoInitializeEx(null, COINIT_APARTMENTTHREADED) >= 0
                try {
                    val pidl = shell32.ILCreateFromPathW(WString(path)) ?: return@runCatching
                    try {
                        shell32.SHOpenFolderAndSelectItems(pidl, 0, null, 0)
                    } finally {
                        shell32.ILFree(pidl)
                    }
                } finally {
                    if (initialized) ole32.CoUninitialize()
                }
            }
        }, "erkin-reveal").apply { isDaemon = true }.start()
    }
}
