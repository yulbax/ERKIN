package io.github.yulbax.erkin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.platform.rememberAppIcon
import io.github.yulbax.erkin.ui.platform.supportsAppIcons

@Composable
fun AppIcon(packageName: String, path: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    if (!supportsAppIcons) return
    val bitmap = rememberAppIcon(packageName, path)
    val shape = RoundedCornerShape(size / 4)
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size).clip(shape), contentScale = ContentScale.Fit)
        return
    }
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(
            name.take(1).uppercase(),
            style = if (size < 24.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AllAppsIcon(size: Dp, modifier: Modifier = Modifier) {
    if (!supportsAppIcons) return
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
