package io.github.yulbax.erkin.desktop

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.tray.api.Tray
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.ui.components.ExitConfirmDialog
import io.github.yulbax.erkin.ui.components.trayLabels
import io.github.yulbax.erkin.ui.di.uiModule
import io.github.yulbax.erkin.ui.platform.ProvideAppLocale
import io.github.yulbax.erkin.ui.screens.MainScreen
import io.github.yulbax.erkin.ui.theme.ERKINTheme
import io.github.yulbax.erkin.util.VpnLauncher
import io.github.yulbax.erkin.vpn.VpnController
import io.github.yulbax.erkin.vpn.VpnStateRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin

fun main() {
    val activations = Channel<Unit>(Channel.CONFLATED)
    val instance = SingleInstance(DesktopPaths.dataDir)
    if (!instance.claim { activations.trySend(Unit) }) return

    val koin = startKoin { modules(desktopModule, uiModule) }.koin
    val controller = koin.get<VpnController>()
    controller.launch()
    autoConnect(koin.get(), koin.get(), koin.get(), koin.get())

    val vpnState = koin.get<VpnStateRepository>().state

    application {
        val icon = remember { BitmapPainter(Image.makeFromEncoded(appIconBytes()).toComposeImageBitmap()) }
        val state = rememberWindowState(width = 440.dp, height = 860.dp)
        var visible by remember { mutableStateOf(true) }
        var confirmExit by remember { mutableStateOf(false) }
        val connection by vpnState.collectAsState()
        val exit = {
            controller.shutdown()
            exitApplication()
        }
        val requestExit = {
            if (connection.isActive || connection.isBusy) {
                visible = true
                confirmExit = true
            } else {
                exit()
            }
        }
        val show = {
            visible = true
            state.isMinimized = false
        }
        LaunchedEffect(Unit) {
            for (activation in activations) show()
        }

        if (TraySupport.isAvailable) ProvideAppLocale {
            val labels = trayLabels()
            @Suppress("DEPRECATION")
            Tray(
                iconPath = resourceUrl(APP_ICON),
                windowsIconPath = resourceUrl(TRAY_ICON_WINDOWS),
                tooltip = "ERKIN",
                primaryAction = show,
                menuContent = {
                    Item(labels.open, onClick = show)
                    Item(labels.exit, onClick = requestExit)
                }
            )
        }

        Window(
            onCloseRequest = requestExit,
            visible = visible,
            title = "ERKIN",
            icon = icon,
            undecorated = true,
            state = state
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = WindowLimits.minimum
                WindowLimits.clampWidth(window)
                WindowsDwm.roundCorners(window)
            }
            LaunchedEffect(state.placement) {
                if (state.placement != WindowPlacement.Floating) state.placement = WindowPlacement.Floating
            }
            LaunchedEffect(state.isMinimized) {
                if (state.isMinimized && TraySupport.isAvailable) {
                    visible = false
                    state.isMinimized = false
                }
            }
            LaunchedEffect(visible) {
                if (visible) window.toFront()
            }
            ProvideAppLocale {
                ERKINTheme {
                    WindowFrame {
                        MainScreen(
                            topBarFrame = { bar -> WindowDraggableArea { bar() } },
                            topBarActions = {
                                CaptionButtons(
                                    onMinimize = { if (TraySupport.isAvailable) visible = false else state.isMinimized = true },
                                    onClose = requestExit
                                )
                            }
                        )
                    }
                    if (confirmExit) {
                        ExitConfirmDialog(
                            onExit = exit,
                            onMinimizeToTray = if (TraySupport.isAvailable) {
                                {
                                    confirmExit = false
                                    visible = false
                                }
                            } else {
                                null
                            },
                            onDismiss = { confirmExit = false }
                        )
                    }
                }
            }
        }
    }
    instance.release()
}

private fun resourceUrl(name: String): String =
    checkNotNull(Thread.currentThread().contextClassLoader.getResource(name)) { "missing $name" }.toString()

private fun appIconBytes(): ByteArray =
    checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(APP_ICON)) { "missing $APP_ICON" }
        .use { it.readBytes() }

private const val APP_ICON = "erkin-icon.png"
private const val TRAY_ICON_WINDOWS = "erkin-icon.ico"

private fun autoConnect(settings: SettingsRepository, profiles: ProfileRepository, apps: AppDao, launcher: VpnLauncher) {
    val shouldConnect = runBlocking {
        settings.settings.first().autoConnect &&
            (profiles.selected.first() != null || apps.getAllAppsSnapshot().any { it.connectionType == ConnectionType.BYEDPI })
    }
    if (shouldConnect) launcher.start()
}
