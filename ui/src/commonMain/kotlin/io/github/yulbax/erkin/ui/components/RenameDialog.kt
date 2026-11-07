package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RenameDialog(current: String, placeholder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    EditorDialog(
        title = stringResource(Res.string.rename),
        onDismiss = onDismiss,
        confirm = DialogAction(stringResource(Res.string.save)) { onSave(name) }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf {
                NameField(name, { name = it }, placeholder)
            }
        )
    }
}

@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, placeholder: String? = null) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.name_label)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        colors = transparentFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
