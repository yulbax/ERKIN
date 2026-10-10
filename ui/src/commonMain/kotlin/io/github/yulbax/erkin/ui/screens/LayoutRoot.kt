package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.components.AppSearchBar
import io.github.yulbax.erkin.ui.platform.LocalMessages
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    topBarFrame: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = navPages.firstOrNull { it.route == currentRoute } ?: Screen.Main

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
    }

    var appsQuery by rememberSaveable { mutableStateOf("") }
    val onAppsScreen = currentRoute == Screen.Apps.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val navigate: (Screen) -> Unit = { screen ->
                scope.launch { drawerState.close() }
                navController.navigateToScreen(screen)
            }
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    primaryNavPages.forEach { screen ->
                        DrawerItem(
                            screen = screen,
                            selected = currentRoute == screen.route,
                            onClick = { navigate(screen) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    secondaryNavPages.forEach { screen ->
                        DrawerItem(
                            screen = screen,
                            selected = currentRoute == screen.route,
                            onClick = { navigate(screen) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                topBarFrame {
                    TopAppBar(
                        title = {
                            if (onAppsScreen) {
                                AppSearchBar(
                                    query = appsQuery,
                                    onQueryChange = { appsQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                            } else {
                                Text(
                                    text = stringResource(currentScreen.title),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(Res.string.open_menu_cd)
                                )
                            }
                        },
                        actions = topBarActions
                    )
                }
            }
        ) { innerPadding ->
            CompositionLocalProvider(LocalMessages provides showMessage) {
                ErkinNavHost(
                    navController = navController,
                    appsQuery = appsQuery,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = null
            )
        },
        label = { Text(stringResource(screen.title)) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
