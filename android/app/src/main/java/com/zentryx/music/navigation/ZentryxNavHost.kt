package com.zentryx.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zentryx.music.ui.screens.LibraryScreen
import com.zentryx.music.ui.screens.LoginScreen
import com.zentryx.music.ui.screens.PlayerScreen
import com.zentryx.music.ui.screens.SearchScreen
import com.zentryx.music.ui.screens.SettingsScreen
import com.zentryx.music.viewmodel.LibraryViewModel
import com.zentryx.music.viewmodel.LoginViewModel
import com.zentryx.music.viewmodel.PlayerViewModel
import com.zentryx.music.viewmodel.SearchViewModel
import com.zentryx.music.viewmodel.SettingsViewModel

@Composable
fun ZentryxNavHost(
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Library.route) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToPlayer = {
                    navController.navigate(Screen.Player.route)
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(Screen.Player.route) {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            PlayerScreen(
                viewModel = playerViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = hiltViewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}
