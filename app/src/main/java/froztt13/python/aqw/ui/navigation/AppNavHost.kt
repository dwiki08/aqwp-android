package froztt13.python.aqw.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import froztt13.python.aqw.ui.screens.DashboardScreen
import froztt13.python.aqw.ui.screens.EclipseScreen
import froztt13.python.aqw.ui.screens.GeneralBotScreen
import froztt13.python.aqw.ui.screens.PlayerStateScreen
import froztt13.python.aqw.ui.screens.SlaveryScreen
import froztt13.python.aqw.ui.screens.TempleScreen
import froztt13.python.aqw.ui.screens.WeeklyDoomBotScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: AppDestination = AppDestination.Dashboard
) {
    CompositionLocalProvider {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
            modifier = modifier
        ) {
            composable<AppDestination.Dashboard> {
                DashboardScreen(
                    onNavigateToTemple = { navController.navigate(AppDestination.Temple) },
                    onNavigateToEclipse = { navController.navigate(AppDestination.Eclipse) },
                    onNavigateToDoom = { navController.navigate(AppDestination.WeeklyDoom) },
                    onNavigateToSlavery = { navController.navigate(AppDestination.Slavery) },
                    onNavigateToGeneral = { navController.navigate(AppDestination.GeneralBot) }
                )
            }

            composable<AppDestination.Temple> {
                TempleScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppDestination.Eclipse> {
                EclipseScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayerState = {
                        navController.navigate(AppDestination.PlayerState)
                    }
                )
            }

            composable<AppDestination.WeeklyDoom> {
                WeeklyDoomBotScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppDestination.Slavery> {
                SlaveryScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppDestination.GeneralBot> {
                GeneralBotScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayerState = {
                        navController.navigate(AppDestination.PlayerState)
                    }
                )
            }

            composable<AppDestination.PlayerState> {
                PlayerStateScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
