package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginDialog(
    hint: String,
    confirmLabel: String,
    initialUsername: String,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    EditorDialog(
        title = stringResource(Res.string.login_title),
        onDismiss = onDismiss,
        confirm = DialogAction(confirmLabel, enabled = username.isNotBlank() && password.isNotEmpty()) {
            onSubmit(username, password)
        }
    ) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = listOf(
                {
                    TextField(
                        value = username,
                        onValueChange = { username = it.replace("\n", "") },
                        label = { Text(stringResource(Res.string.login_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                {
                    TextField(
                        value = password,
                        onValueChange = { password = it.replace("\n", "") },
                        label = { Text(stringResource(Res.string.login_password)) },
                        singleLine = true,
                        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { shown = !shown }) {
                                Icon(
                                    if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(if (shown) Res.string.login_hide_password else Res.string.login_show_password)
                                )
                            }
                        },
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        )
    }
}
