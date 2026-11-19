package com.zentryx.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zentryx.music.ui.screens.DspConsoleScreen
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
    val backStack = remember(startDestination) { mutableStateListOf(startDestination) }
    val forwardStack = remember(startDestination) { mutableStateListOf<String>() }

    fun navigateMenu(route: String) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
        forwardStack.clear()
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    fun goBack() {
        if (backStack.size <= 1) return
        val current = backStack.removeAt(backStack.lastIndex)
        forwardStack.add(current)
        val previous = backStack.last()
        navController.navigate(previous) {
            popUpTo(previous) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun goForward() {
        if (forwardStack.isEmpty()) return
        val next = forwardStack.removeAt(forwardStack.lastIndex)
        backStack.add(next)
        navController.navigate(next) {
            launchSingleTop = true
        }
    }

    val canGoBack = backStack.size > 1
    val canGoForward = forwardStack.isNotEmpty()

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
                    backStack.clear()
                    forwardStack.clear()
                    backStack.add(Screen.Library.route)
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onUseLocalOnly = {
                    navigateMenu(Screen.Library.route)
                }
            )
        }

        composable(Screen.Library.route) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToPlayer = { navigateMenu(Screen.Player.route) },
                onNavigateToSearch = { navigateMenu(Screen.Search.route) },
                onNavigateToSettings = { navigateMenu(Screen.Settings.route) },
                onNavigateToDsp = { navigateMenu(Screen.DspConsole.route) },
                onNavigateBack = { goBack() },
                onNavigateForward = { goForward() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onLogout = {
                    backStack.clear()
                    forwardStack.clear()
                    backStack.add(Screen.Login.route)
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
                onNavigateBack = { goBack() },
                onNavigateForward = { goForward() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onNavigateToDsp = { navigateMenu(Screen.DspConsole.route) }
            )
        }

        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = hiltViewModel()
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { goBack() },
                onNavigateForward = { goForward() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onNavigateToPlayer = { navigateMenu(Screen.Player.route) }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { goBack() },
                onNavigateForward = { goForward() },
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onNavigateToDsp = { navigateMenu(Screen.DspConsole.route) },
                onLoggedOut = {
                    backStack.clear()
                    forwardStack.clear()
                    backStack.add(Screen.Login.route)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(Screen.DspConsole.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            DspConsoleScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { goBack() },
                onNavigateForward = { goForward() },
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }
}
