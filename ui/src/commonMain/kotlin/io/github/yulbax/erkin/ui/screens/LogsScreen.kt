package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.components.VerticalScrollbar
import io.github.yulbax.erkin.ui.platform.rememberSharer
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.util.DiagnosticsSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun Logs() {
    val diagnostics = koinInject<DiagnosticsSource>()
    val sharer = rememberSharer()
    val scope = rememberCoroutineScope()
    val shareLabel = stringResource(Res.string.export_logs)

    var reloadKey by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(reloadKey) {
        text = diagnostics.collect()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { reloadKey++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(
                    stringResource(Res.string.logs_refresh),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        sharer.shareFile(
                            prefix = "erkin-log",
                            extension = "txt",
                            mimeType = "text/plain",
                            content = diagnostics.collect(),
                            title = shareLabel
                        )
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(stringResource(Res.string.share), modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            OutlinedIconButton(
                onClick = { confirmClear = true },
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder()
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(Res.string.logs_clear))
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(Modifier.fillMaxSize()) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp)
                ) {
                    Text(
                        text = text.ifBlank { stringResource(Res.string.logs_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VerticalScrollbar(
                    state = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 12.dp)
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            diagnostics.clear()
                            reloadKey++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.cancel)) }
            },
            title = { Text(stringResource(Res.string.logs_clear_title)) },
            text = { Text(stringResource(Res.string.logs_clear_message)) }
        )
    }
}
