package io.github.yulbax.erkin.ui.platform

import io.github.yulbax.erkin.data.ConnectionType
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import java.io.InputStream
import androidx.compose.ui.text.PlatformTextStyle

interface Sharer {
    fun shareText(text: String, title: String)

    suspend fun shareFile(prefix: String, extension: String, mimeType: String, content: String, title: String)
}

fun interface PickedFile {
    fun open(): InputStream?
}

val LocalMessages = compositionLocalOf<(String) -> Unit> { {} }

@Composable
expect fun rememberShowMessage(): (String) -> Unit

@Composable
expect fun rememberVpnStartRequest(onGranted: () -> Unit): () -> Unit

@Composable
expect fun rememberSharer(): Sharer

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

expect val supportsSystemAppsFilter: Boolean

expect val scrollbarAlwaysVisible: Boolean

expect val drawerSwipeToOpen: Boolean

expect val supportsAppIcons: Boolean

expect val platformNewAppsConnectionType: ConnectionType

expect val platformTextStyle: PlatformTextStyle?
