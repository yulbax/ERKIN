package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.cancel
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = MAX_WIDTH).fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    content = buttons
                )
            }
        }
    }
}

class DialogAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: DialogAction,
    secondary: DialogAction? = null,
    startActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AppDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            startActions()
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = confirm.onClick, enabled = confirm.enabled) { Text(confirm.label) }
            secondary?.let { TextButton(onClick = it.onClick, enabled = it.enabled) { Text(it.label) } }
        },
        content = content
    )
}

private val MAX_WIDTH = 560.dp
