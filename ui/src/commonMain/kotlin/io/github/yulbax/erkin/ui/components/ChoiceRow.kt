package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChoiceRow(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    subtitleIcon: IconSource? = null,
    padding: PaddingValues = PaddingValues(12.dp),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (checked) MaterialTheme.colorScheme.onSurface.copy(alpha = SELECTED_TINT) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().semantics { selected = checked }
    ) {
        Box {
            Row(Modifier.padding(padding).padding(start = MARK_ROOM), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (subtitleIcon != null) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                                    SourceIcon(subtitleIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing()
                }
            }
            if (checked) {
                Box(Modifier.matchParentSize().padding(start = MARK_INSET, top = MARK_INSET, bottom = MARK_INSET)) {
                    Box(
                        Modifier
                            .width(MARK_WIDTH)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(MARK_WIDTH / 2))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

private const val SELECTED_TINT = 0.10f
private val MARK_WIDTH = 4.dp
private val MARK_INSET = 6.dp
private val MARK_ROOM = 8.dp
