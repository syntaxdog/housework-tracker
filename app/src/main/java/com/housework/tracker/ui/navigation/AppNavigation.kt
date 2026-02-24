package com.housework.tracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.housework.tracker.ui.auth.LoginScreen
import com.housework.tracker.ui.house.HouseSetupScreen
import com.housework.tracker.ui.main.MainScreen

object Routes {
    const val LOGIN = "login"
    const val HOUSE_SETUP = "house_setup"
    const val MAIN = "main"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { hasHouse ->
                    val dest = if (hasHouse) Routes.MAIN else Routes.HOUSE_SETUP
                    navController.navigate(dest) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOUSE_SETUP) {
            HouseSetupScreen(
                onHouseReady = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.HOUSE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
