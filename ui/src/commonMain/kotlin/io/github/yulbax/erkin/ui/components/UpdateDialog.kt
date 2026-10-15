package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.cancel
import io.github.yulbax.erkin.ui.res.update_available_text
import io.github.yulbax.erkin.ui.res.update_available_title
import io.github.yulbax.erkin.ui.res.update_download
import io.github.yulbax.erkin.ui.res.update_downloading
import io.github.yulbax.erkin.ui.res.update_failed
import io.github.yulbax.erkin.ui.res.update_later
import io.github.yulbax.erkin.ui.res.update_open_page
import io.github.yulbax.erkin.ui.viewmodel.UpdateUiState
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val openPage = { url: String ->
        viewModel.dismiss()
        runCatching { uriHandler.openUri(url) }
        Unit
    }
    when (val current = state) {
        UpdateUiState.Hidden -> Unit
        is UpdateUiState.Available -> AppDialog(
            title = stringResource(Res.string.update_available_title, current.release.version.toString()),
            onDismiss = viewModel::dismiss,
            buttons = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.update_later)) }
                if (current.installable) {
                    TextButton(onClick = viewModel::update) { Text(stringResource(Res.string.update_download)) }
                } else {
                    TextButton(onClick = { openPage(current.release.pageUrl) }) {
                        Text(stringResource(Res.string.update_open_page))
                    }
                }
            }
        ) {
            Text(stringResource(Res.string.update_available_text, current.current))
            if (current.release.notes.isNotBlank()) {
                val typography = MaterialTheme.typography
                Markdown(
                    content = current.release.notes,
                    typography = markdownTypography(
                        h1 = typography.titleMedium,
                        h2 = typography.titleMedium,
                        h3 = typography.titleSmall,
                        h4 = typography.titleSmall,
                        h5 = typography.titleSmall,
                        h6 = typography.titleSmall
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is UpdateUiState.Downloading -> AppDialog(
            title = stringResource(Res.string.update_downloading, current.release.version.toString()),
            onDismiss = {},
            buttons = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.cancel)) }
            }
        ) {
            val progress = current.progress
            val modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            if (progress == null) LinearProgressIndicator(modifier) else LinearProgressIndicator({ progress }, modifier)
        }
        is UpdateUiState.Failed -> AppDialog(
            title = stringResource(Res.string.update_available_title, current.release.version.toString()),
            onDismiss = viewModel::dismiss,
            buttons = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.update_later)) }
                TextButton(onClick = { openPage(current.release.pageUrl) }) {
                    Text(stringResource(Res.string.update_open_page))
                }
            }
        ) {
            Text(stringResource(Res.string.update_failed))
        }
    }
}
