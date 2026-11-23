package io.github.yulbax.erkin.ui.platform

import androidx.compose.ui.unit.Dp
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.show_all_processes
import io.github.yulbax.erkin.ui.res.show_system_apps
import org.jetbrains.compose.resources.StringResource
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import java.io.InputStream
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily

interface Sharer {
    fun shareText(text: String, title: String)

    suspend fun shareFile(prefix: String, extension: String, mimeType: String, content: String, title: String)
}

interface PickedFile {
    val name: String?

    fun open(): InputStream?
}

val LocalMessages = compositionLocalOf<(String) -> Unit> { {} }

@Composable
expect fun rememberShowMessage(): (String) -> Unit

@Composable
expect fun rememberVpnStartRequest(onGranted: () -> Unit): () -> Unit

@Composable
expect fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit

@Composable
expect fun localNetworkAllowed(): Boolean

@Composable
expect fun rememberSharer(): Sharer

@Composable
expect fun rememberCopyText(confirmation: String): (String) -> Unit

@Composable
expect fun rememberFilePicker(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit

@Composable
expect fun rememberSystemVpnSettings(): (() -> Unit)?

@Composable
expect fun rememberAppIcon(appId: String, path: String?): ImageBitmap?

@Composable
expect fun HoverHint(text: String, enabled: Boolean, content: @Composable () -> Unit)

@Composable
expect fun platformColorScheme(darkTheme: Boolean): ColorScheme?

expect object AppLocale {
    fun currentTag(): String?

    fun apply(tag: String?)
}

enum class HiddenAppsFilter(val label: StringResource) {
    SYSTEM_APPS(Res.string.show_system_apps),
    PROCESSES(Res.string.show_all_processes)
}

expect val hiddenAppsFilter: HiddenAppsFilter?

expect val appsCanBypass: Boolean

val platformChannels: List<ConnectionType> = ConnectionType.entries.filter { appsCanBypass || it != ConnectionType.BYPASS }

expect val revealInFolder: ((path: String) -> Unit)?

expect val scrollbarAlwaysVisible: Boolean

expect val scrollbarEdgeInset: Dp

expect val drawerSwipeToOpen: Boolean

expect val supportsAppIcons: Boolean

@Composable
expect fun flagFontFamily(): FontFamily?

expect val platformNewAppsConnectionType: ConnectionType

expect val platformTextStyle: PlatformTextStyle?
