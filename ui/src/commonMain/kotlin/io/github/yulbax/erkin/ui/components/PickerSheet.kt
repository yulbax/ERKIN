package io.github.yulbax.erkin.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.back
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PickerSheet(
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    content: LazyListScope.(close: (then: () -> Unit) -> Unit) -> Unit
) {
    PickerSheet(
        title = title,
        subtitle = subtitle,
        searchPlaceholder = searchPlaceholder,
        query = query,
        onQueryChange = onQueryChange,
        onDismiss = onDismiss,
        page = Unit,
        pageKey = { it },
        onBack = null
    ) { _, close -> content(close) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <P> PickerSheet(
    title: String,
    subtitle: String?,
    searchPlaceholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    page: P,
    pageKey: (P) -> Any?,
    onBack: (() -> Unit)?,
    content: LazyListScope.(page: P, close: (then: () -> Unit) -> Unit) -> Unit
) {
    val sheetState = expandedSheetState()
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            then()
        }
    }
    val nested = onBack != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = nested,
            onBackCompleted = { onBack?.invoke() }
        )
        Row(
            modifier = Modifier.padding(start = if (nested) 12.dp else 24.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        AppSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = searchPlaceholder,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = page,
            contentKey = pageKey,
            transitionSpec = {
                val towards = if (nested) 1 else -1
                (slideInHorizontally { width -> towards * width } togetherWith slideOutHorizontally { width -> -towards * width })
                    .using(SizeTransform(clip = true))
            }
        ) { shown ->
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) { content(shown, close) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun expandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
)
