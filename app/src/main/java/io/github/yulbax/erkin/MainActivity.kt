package io.github.yulbax.erkin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.ui.screens.MainScreen
import io.github.yulbax.erkin.ui.theme.ERKINTheme
import io.github.yulbax.erkin.ui.theme.rememberDarkTheme
import io.github.yulbax.erkin.ui.viewmodel.IncomingServer
import io.github.yulbax.erkin.ui.viewmodel.IncomingServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    private val incoming: IncomingServers by inject()
    private val profiles: ProfileRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
        setContent {
            val dark = rememberDarkTheme() ?: return@setContent
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            ERKINTheme(darkTheme = dark) {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                profiles.readConfigFile { contentResolver.openInputStream(uri) }?.let { IncomingServer(it, displayName(uri)) }
            }
            opened?.let(incoming::offer)
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
