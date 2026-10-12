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
import froztt13.python.aqw.data.model.UltraBossType
import froztt13.python.aqw.ui.screens.dashboard.DashboardScreen
import froztt13.python.aqw.ui.screens.eclipse.EclipseScreen
import froztt13.python.aqw.ui.screens.general.GeneralBotScreen
import froztt13.python.aqw.ui.screens.player_state.PlayerStateScreen
import froztt13.python.aqw.ui.screens.slavery.SlaveryScreen
import froztt13.python.aqw.ui.screens.temple.TempleScreen
import froztt13.python.aqw.ui.screens.ultra_boss.UltraBossDetailScreen
import froztt13.python.aqw.ui.screens.ultra_boss.UltraBossScreen
import froztt13.python.aqw.ui.screens.weekly_doom.WeeklyDoomBotScreen

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
                    onNavigateToGeneral = { navController.navigate(AppDestination.GeneralBot) },
                    onNavigateToUltraBoss = { navController.navigate(AppDestination.UltraBoss) }
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

            composable<AppDestination.UltraBoss> {
                UltraBossScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToGramiel = { navController.navigate(AppDestination.UltraGramiel) },
                    onNavigateToMalgor = { navController.navigate(AppDestination.UltraMalgor) },
                    onNavigateToDrakath = { navController.navigate(AppDestination.UltraDrakath) }
                )
            }

            composable<AppDestination.UltraGramiel> {
                UltraBossDetailScreen(
                    bossType = UltraBossType.GRAMIEL,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayerState = {
                        navController.navigate(AppDestination.PlayerState)
                    }
                )
            }

            composable<AppDestination.UltraMalgor> {
                UltraBossDetailScreen(
                    bossType = UltraBossType.MALGOR,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayerState = {
                        navController.navigate(AppDestination.PlayerState)
                    }
                )
            }

            composable<AppDestination.UltraDrakath> {
                UltraBossDetailScreen(
                    bossType = UltraBossType.DRAKATH,
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
