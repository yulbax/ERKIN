package io.github.yulbax.erkin.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.exit_confirm_exit
import io.github.yulbax.erkin.ui.res.exit_confirm_text
import io.github.yulbax.erkin.ui.res.exit_confirm_title
import io.github.yulbax.erkin.ui.res.exit_confirm_tray
import io.github.yulbax.erkin.ui.res.tray_open
import org.jetbrains.compose.resources.stringResource

class TrayLabels(val open: String, val exit: String)

@Composable
fun trayLabels(): TrayLabels = TrayLabels(
    open = stringResource(Res.string.tray_open),
    exit = stringResource(Res.string.exit_confirm_exit)
)

@Composable
fun ExitConfirmDialog(onExit: () -> Unit, onMinimizeToTray: (() -> Unit)?, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(Res.string.exit_confirm_title),
        onDismiss = onDismiss,
        buttons = {
            if (onMinimizeToTray != null) {
                TextButton(onClick = onMinimizeToTray) { Text(stringResource(Res.string.exit_confirm_tray)) }
            }
            TextButton(onClick = onExit) { Text(stringResource(Res.string.exit_confirm_exit)) }
        }
    ) {
        Text(stringResource(Res.string.exit_confirm_text))
    }
}
