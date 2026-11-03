package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.components.AppSearchBar
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.components.UpdatePrompt
import io.github.yulbax.erkin.ui.platform.LocalMessages
import io.github.yulbax.erkin.ui.platform.drawerSwipeToOpen
import io.github.yulbax.erkin.ui.screens.apps.AppsSelectionBar
import io.github.yulbax.erkin.ui.viewmodel.AppsMode
import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import io.github.yulbax.erkin.ui.viewmodel.RuleListRefreshViewModel
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import io.github.yulbax.erkin.data.profile.ProfileRepository
import androidx.compose.material3.SnackbarResult
import org.koin.compose.koinInject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.yulbax.erkin.vpn.VpnStateRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    topBarFrame: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    topBarActions: @Composable RowScope.() -> Unit = {},
    updates: UpdateViewModel = koinViewModel()
) {
    koinViewModel<RuleListRefreshViewModel>()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = (navPages + subPages).firstOrNull { it.route == currentRoute } ?: Screen.Main
    val onSubPage = currentScreen in subPages

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
    }
    UnreachableServersMessage(snackbarHostState)

    val appsViewModel: AppsViewModel = koinViewModel()
    val appsMode by appsViewModel.mode.collectAsStateWithLifecycle()
    val onAppsScreen = currentRoute == Screen.Apps.route
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && !onAppsScreen) appsViewModel.stopSelecting()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerSwipeToOpen || drawerState.isOpen,
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
                    val selecting = (appsMode as? AppsMode.Selecting)?.takeIf { onAppsScreen }
                    if (selecting != null) {
                        AppsSelectionBar(
                            count = selecting.packages.size,
                            onCancel = appsViewModel::stopSelecting,
                            onSelectAll = appsViewModel::selectAllVisible,
                            actions = topBarActions
                        )
                    } else TopAppBar(
                        title = {
                            if (onAppsScreen) {
                                AppSearchBar(
                                    query = appsViewModel.query,
                                    onQueryChange = appsViewModel::updateQuery,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    trailing = {
                                        IconButton(
                                            onClick = { appsViewModel.startSelecting() },
                                            modifier = Modifier.padding(end = 1.5.dp)
                                        ) {
                                            Icon(Icons.Filled.Checklist, contentDescription = stringResource(Res.string.apps_select_cd))
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = stringResource(currentScreen.title),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        navigationIcon = {
                            if (onSubPage) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(Res.string.back)
                                    )
                                }
                            } else {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = stringResource(Res.string.open_menu_cd)
                                    )
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.About) {
                                IconButton(onClick = { runCatching { uriHandler.openUri(REPOSITORY_URL) } }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_github),
                                        contentDescription = stringResource(Res.string.about_repository_cd),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            if (currentScreen == Screen.Rules) {
                                IconButton(onClick = { navController.navigate(Screen.RuleSets.route) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = stringResource(Res.string.rule_sets_title)
                                    )
                                }
                            }
                            topBarActions()
                        }
                    )
                }
            }
        ) { innerPadding ->
            CompositionLocalProvider(LocalMessages provides showMessage) {
                ErkinNavHost(
                    navController = navController,
                    appsViewModel = appsViewModel,
                    updates = updates,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            UpdatePrompt(updates)
        }
    }
}

@Composable
private fun DrawerItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { SourceIcon(screen.icon, contentDescription = null) },
        label = { Text(stringResource(screen.title)) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

private const val REPOSITORY_URL = "https://github.com/yulbax/ERKIN"

@Composable
private fun UnreachableServersMessage(snackbarHostState: SnackbarHostState) {
    val states = koinInject<VpnStateRepository>()
    val profiles = koinInject<ProfileRepository>()
    val message = stringResource(Res.string.servers_unreachable_message)
    val useMain = stringResource(Res.string.servers_use_main)
    LaunchedEffect(states, message, useMain) {
        states.stats
            .map { it.unreachableServers }
            .distinctUntilChanged { old, new -> old.isNotEmpty() == new.isNotEmpty() }
            .filter { it.isNotEmpty() }
            .collectLatest { down ->
                val result = snackbarHostState.showSnackbar(message, actionLabel = useMain, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) profiles.unbindServers(down)
            }
    }
}
