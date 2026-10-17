package io.github.yulbax.erkin.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.StringResource
import io.github.yulbax.erkin.ui.screens.apps.Apps
import io.github.yulbax.erkin.ui.screens.chains.Chains
import io.github.yulbax.erkin.ui.screens.erkin.ErkinScreen
import io.github.yulbax.erkin.ui.screens.rules.RuleSets
import io.github.yulbax.erkin.ui.screens.rules.Rules
import io.github.yulbax.erkin.ui.screens.settings.Settings

sealed class Screen(
    val route: String,
    val title: StringResource,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Main : Screen(
        route = "main",
        title = Res.string.erkin,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Filled.Home
    )

    object Apps : Screen(
        route = "apps",
        title = Res.string.applications,
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Filled.List
    )

    object Rules : Screen(
        route = "rules",
        title = Res.string.rules_title,
        selectedIcon = Icons.AutoMirrored.Filled.AltRoute,
        unselectedIcon = Icons.AutoMirrored.Filled.AltRoute
    )

    object RuleSets : Screen(
        route = "rulesets",
        title = Res.string.rule_sets_title,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Filled.Download
    )

    object Chains : Screen(
        route = "chains",
        title = Res.string.chains_title,
        selectedIcon = Icons.Filled.Link,
        unselectedIcon = Icons.Filled.Link
    )

    object Settings : Screen(
        route = "settings",
        title = Res.string.settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Filled.Settings
    )

    object Logs : Screen(
        route = "logs",
        title = Res.string.logs_title,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Filled.Description
    )

    object License : Screen(
        route = "license",
        title = Res.string.license_title,
        selectedIcon = Icons.Filled.Gavel,
        unselectedIcon = Icons.Filled.Gavel
    )

    object About : Screen(
        route = "about",
        title = Res.string.about_title,
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Filled.Info
    )
}

val primaryNavPages = listOf(
    Screen.Main,
    Screen.Apps,
    Screen.Rules,
    Screen.Chains,
    Screen.Settings,
    Screen.Logs
)

val secondaryNavPages = listOf(
    Screen.License,
    Screen.About
)

val subPages = listOf(Screen.RuleSets)

val navPages = primaryNavPages + secondaryNavPages

fun NavController.navigateToScreen(screen: Screen) {
    if (subPages.any { it.route == currentDestination?.route }) popBackStack()
    if (currentDestination?.route == screen.route) return
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ErkinNavHost(
    navController: NavHostController,
    appsQuery: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable(Screen.Main.route) {
            ErkinScreen(onNavigateToApps = { navController.navigateToScreen(Screen.Apps) })
        }
        composable(Screen.Apps.route) { Apps(query = appsQuery) }
        composable(Screen.Rules.route) { Rules(onOpenRuleSets = { navController.navigate(Screen.RuleSets.route) }) }
        composable(Screen.RuleSets.route) { RuleSets() }
        composable(Screen.Chains.route) { Chains() }
        composable(Screen.Settings.route) { Settings() }
        composable(Screen.Logs.route) { Logs() }
        composable(Screen.License.route) { License() }
        composable(Screen.About.route) { About() }
    }
}
