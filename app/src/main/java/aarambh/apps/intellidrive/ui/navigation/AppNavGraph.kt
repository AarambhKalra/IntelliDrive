package aarambh.apps.intellidrive.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import aarambh.apps.intellidrive.ui.screens.auth.LoginScreen
import aarambh.apps.intellidrive.ui.screens.auth.RegisterScreen
import aarambh.apps.intellidrive.ui.screens.dashboard.ParentDashboardScreen
import aarambh.apps.intellidrive.ui.screens.dashboard.StudentDashboardScreen
import aarambh.apps.intellidrive.ui.viewmodel.AuthViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
    authViewModel: AuthViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ── Auth screens ──────────────────────────────────────────────────────

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = { role ->
                    val destination = roleToScreen(role)
                    navController.navigate(destination.route) {
                        // Clear auth screens from back stack
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = { role ->
                    val destination = roleToScreen(role)
                    navController.navigate(destination.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard screens ─────────────────────────────────────────────────

        composable(Screen.StudentDashboard.route) {
            StudentDashboardScreen(
                viewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ParentDashboard.route) {
            ParentDashboardScreen(
                viewModel = authViewModel,
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

/** Maps a Firestore role string to the correct dashboard [Screen]. */
private fun roleToScreen(role: String): Screen = when (role) {
    "parent"     -> Screen.ParentDashboard
    else         -> Screen.StudentDashboard   // default / "student"
}
