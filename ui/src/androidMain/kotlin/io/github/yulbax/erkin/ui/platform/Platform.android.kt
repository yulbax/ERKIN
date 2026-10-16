package io.github.yulbax.erkin.ui.platform

import io.github.yulbax.erkin.data.ConnectionType
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily

@Composable
actual fun rememberShowMessage(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() } }
}

@Composable
actual fun rememberVpnStartRequest(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) onGranted()
    }
    val requestConsent = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) consentLauncher.launch(prepareIntent) else onGranted()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestConsent() }

    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestConsent()
        }
    }
}

@Composable
actual fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) {
            onResult(true)
        } else {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}

@Composable
actual fun rememberSharer(): Sharer {
    val context = LocalContext.current
    return remember(context) { AndroidSharer(context) }
}

private class AndroidSharer(private val context: Context) : Sharer {
    override fun shareText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, title)) }
    }

    override suspend fun shareFile(prefix: String, extension: String, mimeType: String, content: String, title: String) {
        val uri = ShareFiles.write(context, prefix, extension, content)
        runCatching {
            context.startActivity(
                Intent.createChooser(ShareFiles.intent(uri, mimeType), title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, onPicked: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked { context.contentResolver.openInputStream(uri) }
    }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

@Composable
actual fun rememberSystemVpnSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
actual fun rememberAppIcon(appId: String, path: String?): ImageBitmap? {
    val context = LocalContext.current
    return produceState(initialValue = AppIconCache.get(appId), key1 = appId) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppIconCache.load(context, appId) }
        }
    }.value
}

private object AppIconCache {
    private const val SIZE_PX = 128
    private const val BYTES_PER_ICON = SIZE_PX * SIZE_PX * 4

    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = BYTES_PER_ICON
    }

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = createBitmap(SIZE_PX, SIZE_PX)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()?.also { cache.put(packageName, it) }
    }
}

@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual object AppLocale {
    actual fun currentTag(): String? = AppCompatDelegate.getApplicationLocales()[0]?.language

    actual fun apply(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}

actual val platformNewAppsConnectionType: ConnectionType = ConnectionType.VPN

actual val supportsSystemAppsFilter: Boolean = true

actual val rulesCoverAllApps: Boolean = false

actual val revealInFolder: ((path: String) -> Unit)? = null

actual val scrollbarAlwaysVisible: Boolean = false

actual val drawerSwipeToOpen: Boolean = true

actual val supportsAppIcons: Boolean = true

@Composable
actual fun flagFontFamily(): FontFamily? = null

actual val platformTextStyle: PlatformTextStyle? = null

@Composable
actual fun HoverHint(text: String, enabled: Boolean, content: @Composable () -> Unit) = content()
