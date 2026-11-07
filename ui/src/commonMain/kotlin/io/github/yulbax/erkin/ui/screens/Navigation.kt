package io.github.yulbax.erkin.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import io.github.yulbax.erkin.ui.screens.connections.AppConnections
import io.github.yulbax.erkin.ui.screens.connections.Connections
import java.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yulbax.erkin.ui.components.ChainIcon
import io.github.yulbax.erkin.ui.components.IconSource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.StringResource
import io.github.yulbax.erkin.ui.screens.apps.Apps
import io.github.yulbax.erkin.ui.screens.byedpi.ByeDpi
import io.github.yulbax.erkin.ui.screens.byedpi.SiteLists
import io.github.yulbax.erkin.ui.screens.chains.Chains
import io.github.yulbax.erkin.ui.screens.docs.DocArticle
import io.github.yulbax.erkin.ui.screens.docs.DocPage
import io.github.yulbax.erkin.ui.screens.docs.Docs
import io.github.yulbax.erkin.ui.screens.erkin.ErkinScreen
import io.github.yulbax.erkin.ui.screens.rules.RuleSets
import io.github.yulbax.erkin.ui.screens.rules.Rules
import io.github.yulbax.erkin.ui.screens.settings.Settings
import io.github.yulbax.erkin.ui.viewmodel.AppsViewModel
import io.github.yulbax.erkin.ui.viewmodel.UpdateViewModel

sealed class Screen(
    val route: String,
    val title: StringResource,
    val icon: IconSource
) {
    object Main : Screen(
        route = "main",
        title = Res.string.erkin,
        icon = IconSource.Vector(Icons.Filled.Home)
    )

    object Apps : Screen(
        route = "apps",
        title = Res.string.applications,
        icon = IconSource.Vector(Icons.AutoMirrored.Filled.List)
    )

    object Connections : Screen(
        route = "connections",
        title = Res.string.connections_title,
        icon = IconSource.Vector(Icons.Filled.SwapVert)
    )

    object ConnectionApp : Screen(
        route = "connections/{$CONNECTION_APP}",
        title = Res.string.connections_title,
        icon = IconSource.Vector(Icons.Filled.SwapVert)
    ) {
        fun routeOf(app: String): String = "connections/${ENCODER.encodeToString(app.encodeToByteArray())}"

        fun appOf(argument: String?): String? = argument?.let { runCatching { DECODER.decode(it).decodeToString() }.getOrNull() }

        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }

    object ByeDpi : Screen(
        route = "byedpi",
        title = Res.string.byedpi_title,
        icon = IconSource.Drawable(Res.drawable.ic_byedpi)
    )

    object Rules : Screen(
        route = "rules",
        title = Res.string.rules_title,
        icon = IconSource.Vector(Icons.AutoMirrored.Filled.AltRoute)
    )

    object RuleSets : Screen(
        route = "rulesets",
        title = Res.string.rule_sets_title,
        icon = IconSource.Vector(Icons.Filled.Download)
    )

    object SiteLists : Screen(
        route = "byedpi-sites",
        title = Res.string.byedpi_site_lists,
        icon = IconSource.Vector(Icons.Filled.Settings)
    )

    object Chains : Screen(
        route = "chains",
        title = Res.string.chains_title,
        icon = ChainIcon
    )

    object Settings : Screen(
        route = "settings",
        title = Res.string.settings,
        icon = IconSource.Vector(Icons.Filled.Settings)
    )

    object Logs : Screen(
        route = "logs",
        title = Res.string.logs_title,
        icon = IconSource.Vector(Icons.Filled.Description)
    )

    object Support : Screen(
        route = "support",
        title = Res.string.support_title,
        icon = IconSource.Vector(Icons.Filled.Favorite)
    )

    object Docs : Screen(
        route = "docs",
        title = Res.string.docs_title,
        icon = IconSource.Vector(Icons.AutoMirrored.Filled.HelpOutline)
    )

    object DocPage : Screen(
        route = "docs/{$DOC_SLUG}",
        title = Res.string.docs_title,
        icon = IconSource.Vector(Icons.AutoMirrored.Filled.HelpOutline)
    ) {
        fun routeOf(article: DocArticle): String = "docs/${article.slug}"
    }

    object License : Screen(
        route = "license",
        title = Res.string.license_title,
        icon = IconSource.Vector(Icons.Filled.Gavel)
    )

    object About : Screen(
        route = "about",
        title = Res.string.about_title,
        icon = IconSource.Vector(Icons.Filled.Info)
    )
}

private const val DOC_SLUG = "slug"
private const val CONNECTION_APP = "app"

val primaryNavPages = listOf(
    Screen.Main,
    Screen.Apps,
    Screen.Connections,
    Screen.Rules,
    Screen.Chains,
    Screen.ByeDpi,
    Screen.Settings,
    Screen.Logs
)

val secondaryNavPages = listOf(
    Screen.Support,
    Screen.Docs,
    Screen.License,
    Screen.About
)

val subPages = listOf(Screen.RuleSets, Screen.SiteLists, Screen.DocPage, Screen.ConnectionApp)

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
    appsViewModel: AppsViewModel,
    updates: UpdateViewModel,
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
            ErkinScreen(
                onNavigateToApps = { navController.navigateToScreen(Screen.Apps) },
                onNavigateToByeDpi = { navController.navigateToScreen(Screen.ByeDpi) }
            )
        }
        composable(Screen.Apps.route) { Apps(viewModel = appsViewModel) }
        composable(Screen.Connections.route) { Connections(onOpenApp = { navController.navigate(Screen.ConnectionApp.routeOf(it)) }) }
        composable(Screen.ConnectionApp.route) { entry ->
            Screen.ConnectionApp.appOf(entry.savedStateHandle.get<String>(CONNECTION_APP))?.let { AppConnections(it) }
        }
        composable(Screen.ByeDpi.route) { ByeDpi() }
        composable(Screen.SiteLists.route) { SiteLists() }
        composable(Screen.Rules.route) { Rules(onOpenRuleSets = { navController.navigate(Screen.RuleSets.route) }) }
        composable(Screen.RuleSets.route) { RuleSets() }
        composable(Screen.Chains.route) { Chains() }
        composable(Screen.Settings.route) { Settings(updates = updates) }
        composable(Screen.Logs.route) { Logs() }
        composable(Screen.Support.route) { Support() }
        composable(Screen.Docs.route) { Docs(onOpen = { navController.navigate(Screen.DocPage.routeOf(it)) }) }
        composable(Screen.DocPage.route) { entry ->
            DocArticle.fromSlug(entry.savedStateHandle.get<String>(DOC_SLUG))?.let { DocPage(it) }
        }
        composable(Screen.License.route) { License() }
        composable(Screen.About.route) { About() }
    }
}
