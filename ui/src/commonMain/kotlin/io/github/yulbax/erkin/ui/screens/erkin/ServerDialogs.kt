package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.platform.PickedFile
import io.github.yulbax.erkin.ui.platform.rememberFilePicker
import io.github.yulbax.erkin.ui.platform.rememberShowMessage

@Composable
internal fun EditServerDialog(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, link: String) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var link by remember { mutableStateOf(profile.link) }

    AppDialog(
        title = stringResource(Res.string.edit_server),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { onSave(name, link) }) { Text(stringResource(Res.string.save)) }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf(
                {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.name_label)) },
                        singleLine = true,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    TextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text(stringResource(Res.string.share_link_label)) },
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        )
    }
}

@Composable
internal fun AddServerDialog(
    onDismiss: () -> Unit,
    onReadFile: (PickedFile, (String?) -> Unit) -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val showMessage = rememberShowMessage()
    val invalidFileMessage = stringResource(Res.string.import_invalid_file)
    val pickFile = rememberFilePicker(listOf("text/plain", "*/*")) { file ->
        onReadFile(file) { content ->
            if (content == null) showMessage(invalidFileMessage) else text = content.trim()
        }
    }

    AppDialog(
        title = stringResource(Res.string.add_server),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = pickFile) { Text(stringResource(Res.string.from_file)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = { if (text.isNotBlank()) onAdd(text) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(Res.string.add)) }
        }
    ) {
        Text(
            text = stringResource(Res.string.add_server_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.link_or_subscription_url)) },
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
