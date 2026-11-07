package io.github.yulbax.erkin.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.cancel
import io.github.yulbax.erkin.ui.res.delete
import org.jetbrains.compose.resources.stringResource

@Composable
fun DestructiveButton(onClick: () -> Unit, label: String = stringResource(Res.string.delete)) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) { Text(label) }
}

@Composable
fun DestructiveConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmLabel: String = stringResource(Res.string.delete)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { DestructiveButton(onConfirm, confirmLabel) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        title = title,
        text = text
    )
}
