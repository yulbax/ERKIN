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
import androidx.compose.ui.unit.Dp
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
import java.io.InputStream
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
import io.github.yulbax.erkin.ui.res.share_copied
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.Font

@Composable
actual fun rememberShowMessage(): (String) -> Unit = LocalMessages.current

@Composable
actual fun rememberVpnStartRequest(onGranted: () -> Unit): () -> Unit = onGranted

@Composable
actual fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

@Composable
actual fun localNetworkAllowed(): Boolean = true

@Composable
actual fun rememberSharer(): Sharer {
    val copy = rememberCopyText(stringResource(Res.string.share_copied))
    return remember(copy) { DesktopSharer(copy) }
}

private class DesktopSharer(private val copy: (String) -> Unit) : Sharer {
    override fun shareText(text: String, title: String) = copy(text)

    override suspend fun shareFile(prefix: String, extension: String, mimeType: String, content: String, title: String) {
        val target = chooseFile(title, FileDialog.SAVE, "$prefix.$extension") ?: return
        withContext(Dispatchers.IO) { target.writeText(content) }
    }
}

@Composable
actual fun rememberCopyText(confirmation: String): (String) -> Unit {
    val showMessage = LocalMessages.current
    return remember(showMessage, confirmation) {
        { text ->
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            showMessage(confirmation)
        }
    }
}

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit = {
    chooseFile(title = "", mode = FileDialog.LOAD, suggestedName = null)?.let { file ->
        onPicked(object : PickedFile {
            override val name: String = file.name

            override fun open(): InputStream = file.inputStream()
        })
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
    private val PREFERENCE_NODES = listOf("io/github/yulbax/erkin", "io/github/yulbax/frkn")
    private val systemLocale: Locale = Locale.getDefault()
    private var file: File? = null
    private var selected: String? = null

    val revision = mutableIntStateOf(0)

    fun storeIn(file: File, importFromPreferences: Boolean) {
        this.file = file
        val stored = runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        selected = (stored ?: if (importFromPreferences) importedLanguage(file) else null)?.ifBlank { null }
        selected?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
    }

    actual fun currentTag(): String? = selected

    private fun importedLanguage(file: File): String? = runCatching {
        val root = Preferences.userRoot()
        PREFERENCE_NODES.firstNotNullOfOrNull { node -> root.takeIf { it.nodeExists(node) }?.node(node)?.get(KEY, null) }
            ?.also { file.writeText(it) }
    }.getOrNull()

    actual fun apply(tag: String?) {
        selected = tag
        Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: systemLocale)
        runCatching { file?.let { if (tag == null) it.delete() else it.writeText(tag) } }
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

private val isLinux: Boolean = System.getProperty("os.name").startsWith("Linux")

actual val platformNewAppsConnectionType: ConnectionType = if (isLinux) ConnectionType.VPN else ConnectionType.DIRECT

actual val hiddenApps: HiddenApps = HiddenApps.PROCESSES

actual val appsCanBypass: Boolean = false

actual val scrollbarEdgeInset: Dp = 6.dp

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
