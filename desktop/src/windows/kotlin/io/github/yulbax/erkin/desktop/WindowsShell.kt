package io.github.yulbax.erkin.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.ImageIcon
import javax.swing.filechooser.FileSystemView

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

    fun icon(path: String, size: Int): BufferedImage? {
        val file = File(path).takeIf { it.isFile } ?: return null
        val icon = runCatching { FileSystemView.getFileSystemView().getSystemIcon(file, size, size) }.getOrNull()
            ?: return null
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            if (icon is ImageIcon) {
                graphics.drawImage(icon.image, 0, 0, size, size, null)
            } else {
                icon.paintIcon(null, graphics, (size - icon.iconWidth) / 2, (size - icon.iconHeight) / 2)
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

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
