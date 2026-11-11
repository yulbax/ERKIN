package io.github.yulbax.erkin.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    actionColor: Color = SnackbarDefaults.actionColor
) {
    SnackbarHost(hostState, modifier) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        LaunchedEffect(swipe.currentValue) {
            if (swipe.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
        }
        SwipeToDismissBox(state = swipe, backgroundContent = {}) {
            Snackbar(data, containerColor = containerColor, contentColor = contentColor, actionColor = actionColor)
        }
    }
}
