package io.github.yulbax.erkin.ui.screens.erkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class SetupStep(val label: StringResource) {
    Server(Res.string.setup_step_server),
    Apps(Res.string.setup_step_apps),
    Connect(Res.string.setup_step_connect)
}

@Composable
internal fun SetupChecklist(
    done: (SetupStep) -> Boolean,
    onStep: (SetupStep) -> (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.setup_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.dismiss),
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            SetupStep.entries.forEach { step ->
                val complete = done(step)
                val action = if (complete) null else onStep(step)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        if (complete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (complete) MaterialTheme.colorScheme.primary else color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(step.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        modifier = Modifier.weight(1f)
                    )
                    if (action != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = color
                        )
                    }
                }
            }
        }
    }
}
