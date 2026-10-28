package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yulbax.erkin.ui.components.EmptyState
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

    val entries by diagnostics.feed.entries.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) -> if (scrolling) following = !canScrollForward }
    }
    LaunchedEffect(entries.size) {
        if (following && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
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
                if (entries.isEmpty()) {
                    EmptyState(stringResource(Res.string.logs_empty), Modifier.align(Alignment.Center))
                }
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(entries) { entry ->
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    state = listState,
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
                        scope.launch { diagnostics.clear() }
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
