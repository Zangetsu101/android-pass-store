package com.example.pass.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.pass.browser.EntryBrowserScreen
import com.example.pass.browser.EntryBrowserViewModel
import com.example.pass.onboarding.OnboardingCloneScreen
import com.example.pass.onboarding.OnboardingGpgImportScreen
import com.example.pass.onboarding.OnboardingRemoteUrlScreen
import com.example.pass.onboarding.OnboardingSshKeyScreen
import com.example.pass.onboarding.OnboardingViewModel
import com.example.pass.preferences.AppPreferences
import com.example.pass.session.SessionStartScreen
import com.example.pass.session.SessionStartViewModel
import com.example.pass.settings.SettingsScreen
import com.example.pass.settings.SettingsViewModel
import com.example.pass.syncpanel.SyncPanelScreen
import com.example.pass.syncpanel.SyncPanelViewModel
import com.example.pass.ui.components.PassScaffold
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object OnboardingRemoteUrl : NavKey
@Serializable data object OnboardingSshKey : NavKey
@Serializable data object OnboardingGpgImport : NavKey
@Serializable data object OnboardingClone : NavKey
@Serializable data object SessionStart : NavKey
@Serializable data object EntryBrowser : NavKey
@Serializable data object SyncPanel : NavKey
@Serializable data object Settings : NavKey

@Composable
fun PassDroidNavHost(appPreferences: AppPreferences) {
    val backStack = rememberNavBackStack(Splash)
    val onboardingVm: OnboardingViewModel = hiltViewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Splash> {
                LaunchedEffect(Unit) {
                    combine(appPreferences.remoteUrl, appPreferences.gpgImported) { url, gpgDone ->
                        url to gpgDone
                    }.collect { (url, gpgDone) ->
                        val dest = when {
                            url.isNotEmpty() -> EntryBrowser
                            gpgDone -> OnboardingGpgImport
                            else -> OnboardingRemoteUrl
                        }
                        backStack.clear()
                        backStack.add(dest)
                    }
                }
                PassScaffold { _ ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            entry<OnboardingRemoteUrl> {
                OnboardingRemoteUrlScreen(onboardingVm) {
                    backStack.add(OnboardingSshKey)
                }
            }
            entry<OnboardingSshKey> {
                OnboardingSshKeyScreen(onboardingVm) {
                    backStack.add(OnboardingGpgImport)
                }
            }
            entry<OnboardingGpgImport> {
                OnboardingGpgImportScreen(onboardingVm) {
                    backStack.add(OnboardingClone)
                }
            }
            entry<OnboardingClone> {
                OnboardingCloneScreen(onboardingVm) {
                    backStack.clear()
                    backStack.add(EntryBrowser)
                }
            }

            entry<SessionStart> {
                val vm: SessionStartViewModel = hiltViewModel()
                SessionStartScreen(
                    viewModel = vm,
                    onSuccess = { backStack.removeLastOrNull() },
                )
            }

            entry<EntryBrowser> {
                val vm: EntryBrowserViewModel = hiltViewModel()
                EntryBrowserScreen(
                    viewModel = vm,
                    onNavigateToSync = { backStack.add(SyncPanel) },
                    onNavigateToSettings = { backStack.add(Settings) },
                    onNavigateToSessionStart = { backStack.add(SessionStart) },
                )
            }

            entry<SyncPanel> {
                val vm: SyncPanelViewModel = hiltViewModel()
                SyncPanelScreen(vm, onBack = { backStack.removeLastOrNull() })
            }

            entry<Settings> {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = vm,
                    onBack = { backStack.removeLastOrNull() },
                    onClearedData = {
                        backStack.clear()
                        backStack.add(OnboardingRemoteUrl)
                    },
                )
            }
        },
    )
}
