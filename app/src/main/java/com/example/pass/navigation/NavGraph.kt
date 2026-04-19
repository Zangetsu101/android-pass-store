package com.example.pass.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.pass.browser.EntryBrowserScreen
import com.example.pass.browser.EntryBrowserViewModel
import com.example.pass.onboarding.OnboardingBiometricScreen
import com.example.pass.onboarding.OnboardingCloneScreen
import com.example.pass.onboarding.OnboardingGpgImportScreen
import com.example.pass.onboarding.OnboardingRemoteUrlScreen
import com.example.pass.onboarding.OnboardingSshKeyScreen
import com.example.pass.onboarding.OnboardingViewModel
import com.example.pass.preferences.AppPreferences
import com.example.pass.settings.SettingsScreen
import com.example.pass.settings.SettingsViewModel
import com.example.pass.syncpanel.SyncPanelScreen
import com.example.pass.syncpanel.SyncPanelViewModel
import kotlinx.serialization.Serializable

@Serializable object Splash
@Serializable object OnboardingRoot
@Serializable object OnboardingRemoteUrl
@Serializable object OnboardingSshKey
@Serializable object OnboardingGpgImport
@Serializable object OnboardingBiometric
@Serializable object OnboardingClone
@Serializable object EntryBrowser
@Serializable object SyncPanel
@Serializable object Settings

@Composable
fun PassDroidNavHost(appPreferences: AppPreferences) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Splash) {

        composable<Splash> {
            val remoteUrl: String? by appPreferences.remoteUrl.collectAsState(initial = null)
            LaunchedEffect(remoteUrl) {
                val url = remoteUrl ?: return@LaunchedEffect  // null = still loading
                val dest = if (url.isEmpty()) OnboardingRoot else EntryBrowser
                navController.navigate(dest) {
                    popUpTo<Splash> { inclusive = true }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        navigation<OnboardingRoot>(startDestination = OnboardingRemoteUrl) {
            composable<OnboardingRemoteUrl> { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry<OnboardingRoot>() }
                val vm: OnboardingViewModel = hiltViewModel(parentEntry)
                OnboardingRemoteUrlScreen(vm) {
                    navController.navigate(OnboardingSshKey)
                }
            }
            composable<OnboardingSshKey> { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry<OnboardingRoot>() }
                val vm: OnboardingViewModel = hiltViewModel(parentEntry)
                OnboardingSshKeyScreen(vm) {
                    navController.navigate(OnboardingGpgImport)
                }
            }
            composable<OnboardingGpgImport> { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry<OnboardingRoot>() }
                val vm: OnboardingViewModel = hiltViewModel(parentEntry)
                OnboardingGpgImportScreen(vm) {
                    navController.navigate(OnboardingBiometric)
                }
            }
            composable<OnboardingBiometric> {
                OnboardingBiometricScreen {
                    navController.navigate(OnboardingClone)
                }
            }
            composable<OnboardingClone> { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry<OnboardingRoot>() }
                val vm: OnboardingViewModel = hiltViewModel(parentEntry)
                OnboardingCloneScreen(vm) {
                    navController.navigate(EntryBrowser) {
                        popUpTo<OnboardingRoot> { inclusive = true }
                    }
                }
            }
        }

        composable<EntryBrowser> {
            val vm: EntryBrowserViewModel = hiltViewModel()
            EntryBrowserScreen(
                viewModel = vm,
                onNavigateToSync = { navController.navigate(SyncPanel) },
                onNavigateToSettings = { navController.navigate(Settings) },
            )
        }

        composable<SyncPanel> {
            val vm: SyncPanelViewModel = hiltViewModel()
            SyncPanelScreen(vm, onBack = { navController.popBackStack() })
        }

        composable<Settings> {
            val vm: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onClearedData = {
                    navController.navigate(OnboardingRoot) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
