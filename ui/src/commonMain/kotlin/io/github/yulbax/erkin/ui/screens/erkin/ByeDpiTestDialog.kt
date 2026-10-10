package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.theme.StatusGreen
import io.github.yulbax.erkin.ui.viewmodel.ConnectionViewModel
import io.github.yulbax.erkin.vpn.ByeDpiSites

@Composable
fun ByeDpiTestDialog(
    test: ConnectionViewModel.ByeDpiTestState,
    onRunFull: () -> Unit,
    onDismiss: () -> Unit
) {
    val reachable = test.results.count { it.reachable }
    val done = test.results.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.byedpi_test_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.byedpi_test_summary, reachable, done, test.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (test.running && test.total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / test.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                ) {
                    test.results.groupBy { it.group }.forEach { (group, rows) ->
                        item {
                            Text(
                                text = "$group · ${rows.count { it.reachable }}/${rows.size}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(rows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (row.reachable) "✓" else "✗",
                                    color = if (row.reachable) StatusGreen
                                    else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = row.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRunFull, enabled = !test.running) {
                Text(stringResource(Res.string.byedpi_test_full, ByeDpiSites.fullTotal))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ok)) }
        }
    )
}
