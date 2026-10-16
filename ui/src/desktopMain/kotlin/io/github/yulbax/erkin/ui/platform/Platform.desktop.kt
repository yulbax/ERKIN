package io.github.yulbax.erkin.ui.platform

import io.github.yulbax.erkin.data.ConnectionType
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.util.prefs.Preferences
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.swing.ImageIcon
import javax.swing.filechooser.FileSystemView
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import java.awt.RenderingHints
import androidx.compose.ui.text.font.FontFamily
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.noto_flags
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberShowMessage(): (String) -> Unit = LocalMessages.current

@Composable
actual fun rememberVpnStartRequest(onGranted: () -> Unit): () -> Unit = onGranted

@Composable
actual fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

@Composable
actual fun rememberSharer(): Sharer = remember { DesktopSharer() }

private class DesktopSharer : Sharer {
    override fun shareText(text: String, title: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    override suspend fun shareFile(prefix: String, extension: String, mimeType: String, content: String, title: String) {
        val target = chooseFile(title, FileDialog.SAVE, "$prefix.$extension") ?: return
        withContext(Dispatchers.IO) { target.writeText(content) }
    }
}

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit = {
    chooseFile(title = "", mode = FileDialog.LOAD, suggestedName = null)?.let { file ->
        onPicked { file.inputStream() }
    }
}

@Composable
actual fun rememberSystemVpnSettings(): (() -> Unit)? = null

@Composable
actual fun rememberAppIcon(appId: String, path: String?): ImageBitmap? =
    produceState(initialValue = path?.let(ExecutableIcons::cached), key1 = path) {
        if (value == null && path != null) value = withContext(Dispatchers.IO) { ExecutableIcons.load(path) }
    }.value

private object ExecutableIcons {
    private const val SIZE = 64
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    fun cached(path: String): ImageBitmap? = icons[path]

    fun load(path: String): ImageBitmap? {
        if (!supportsAppIcons) return null
        icons[path]?.let { return it }
        val file = File(path).takeIf { it.isFile } ?: return null
        val icon = runCatching { FileSystemView.getFileSystemView().getSystemIcon(file, SIZE, SIZE) }.getOrNull()
            ?: return null
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            if (icon is ImageIcon) {
                graphics.drawImage(icon.image, 0, 0, SIZE, SIZE, null)
            } else {
                icon.paintIcon(null, graphics, (SIZE - icon.iconWidth) / 2, (SIZE - icon.iconHeight) / 2)
            }
        } finally {
            graphics.dispose()
        }
        return image.toComposeImageBitmap().also { icons[path] = it }
    }
}

@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme? = null

actual object AppLocale {
    private const val KEY = "language"
    private const val LEGACY_NODE = "io/github/yulbax/frkn"
    private val systemLocale: Locale = Locale.getDefault()
    private val preferences: Preferences? = runCatching { Preferences.userRoot().node("io/github/yulbax/erkin") }.getOrNull()
    private var selected: String? = runCatching { preferences?.get(KEY, null) ?: legacyLanguage() }.getOrNull()?.ifBlank { null }

    val revision = mutableIntStateOf(0)

    init {
        selected?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
    }

    actual fun currentTag(): String? = selected

    private fun legacyLanguage(): String? = Preferences.userRoot()
        .takeIf { it.nodeExists(LEGACY_NODE) }
        ?.node(LEGACY_NODE)
        ?.get(KEY, null)

    actual fun apply(tag: String?) {
        selected = tag
        Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: systemLocale)
        runCatching {
            if (tag == null) preferences?.remove(KEY) else preferences?.put(KEY, tag)
            preferences?.flush()
        }
        revision.intValue++
    }
}

@Composable
fun ProvideAppLocale(content: @Composable () -> Unit) {
    val revision = AppLocale.revision.intValue
    val density = LocalDensity.current
    val localeDensity = remember(density, revision) { object : Density by density {} }
    CompositionLocalProvider(LocalDensity provides localeDensity, content = content)
}

private fun chooseFile(title: String, mode: Int, suggestedName: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    suggestedName?.let { dialog.file = it }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

actual val platformNewAppsConnectionType: ConnectionType = ConnectionType.DIRECT

actual val supportsSystemAppsFilter: Boolean = false

actual val rulesCoverAllApps: Boolean = true

actual val revealInFolder: ((path: String) -> Unit)? =
    if (WindowsShell.isWindows) { path -> if (File(path).isFile) WindowsShell.revealInExplorer(path) } else null

actual val scrollbarAlwaysVisible: Boolean = true

actual val drawerSwipeToOpen: Boolean = false

actual val supportsAppIcons: Boolean = System.getProperty("os.name").startsWith("Windows")

@Composable
actual fun flagFontFamily(): FontFamily? = FontFamily(Font(Res.font.noto_flags))

@OptIn(ExperimentalTextApi::class)
actual val platformTextStyle: PlatformTextStyle? by lazy {
    if (!systemUsesRgbClearType()) return@lazy null
    val defaults = FontRasterizationSettings.PlatformDefault
    PlatformTextStyle(
        spanStyle = null,
        paragraphStyle = PlatformParagraphStyle(
            FontRasterizationSettings(
                smoothing = FontSmoothing.SubpixelAntiAlias,
                hinting = defaults.hinting,
                subpixelPositioning = defaults.subpixelPositioning,
                autoHintingForced = defaults.autoHintingForced
            )
        )
    )
}

private fun systemUsesRgbClearType(): Boolean {
    val hints = runCatching { Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") }
        .getOrNull() as? Map<*, *> ?: return false
    return hints[RenderingHints.KEY_TEXT_ANTIALIASING] == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun HoverHint(text: String, enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))
    ) {
        content()
    }
}
