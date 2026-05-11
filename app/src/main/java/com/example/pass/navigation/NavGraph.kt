package com.example.pass.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.pass.browser.EntryBrowserScreen
import com.example.pass.browser.EntryBrowserViewModel
import com.example.pass.browser.EntryDetailScreen
import com.example.pass.browser.EntryDetailViewModel
import com.example.pass.onboarding.CloneProgressScreen
import com.example.pass.onboarding.CloneProgressViewModel
import com.example.pass.onboarding.CloneRepoScreen
import com.example.pass.onboarding.CloneRepoViewModel
import com.example.pass.onboarding.GpgImportViewModel
import com.example.pass.onboarding.OnboardingGpgImportScreen
import com.example.pass.onboarding.WelcomeScreen
import com.example.pass.preferences.AppPreferences
import com.example.pass.session.SessionStartScreen
import com.example.pass.session.SessionStartViewModel
import com.example.pass.settings.SettingsScreen
import com.example.pass.settings.SettingsViewModel
import com.example.pass.syncpanel.SyncPanelScreen
import com.example.pass.syncpanel.SyncPanelViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable data object Splash : NavKey

@Serializable data object Welcome : NavKey

@Serializable data object CloneRepo : NavKey

@Serializable data class OnboardingGpgImport(
    val remoteUrl: String,
) : NavKey

@Serializable data class OnboardingClone(
    val remoteUrl: String,
) : NavKey

@Serializable data class SessionStart(
    val returnEntryPath: String? = null,
) : NavKey

@Serializable data object EntryBrowser : NavKey

@Serializable data class EntryDetail(
    val entryPath: String,
) : NavKey

@Serializable data object SyncPanel : NavKey

@Serializable data object Settings : NavKey

@Composable
fun PassDroidNavHost(appPreferences: AppPreferences) {
    val backStack = rememberNavBackStack(Splash)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Splash> {
                    LaunchedEffect(Unit) {
                        val destDeferred =
                            async {
                                combine(appPreferences.remoteUrl, appPreferences.gpgImported) { url, gpgDone ->
                                    url to gpgDone
                                }.first().let { (url, gpgDone) ->
                                    when {
                                        url.isNotEmpty() -> EntryBrowser
                                        gpgDone -> CloneRepo
                                        else -> Welcome
                                    }
                                }
                            }
                        delay(1_500.milliseconds)
                        val dest = destDeferred.await()
                        backStack.clear()
                        backStack.add(dest)
                    }
                    SplashScreen()
                }

                entry<Welcome> {
                    WelcomeScreen(onStart = { backStack.add(CloneRepo) })
                }

                entry<CloneRepo> {
                    val vm: CloneRepoViewModel = hiltViewModel()
                    CloneRepoScreen(vm) { url ->
                        backStack.add(OnboardingGpgImport(url))
                    }
                }

                entry<OnboardingGpgImport> {
                    val vm: GpgImportViewModel = hiltViewModel()
                    OnboardingGpgImportScreen(vm) {
                        backStack.add(OnboardingClone(it.remoteUrl))
                    }
                }

                entry<OnboardingClone> {
                    val vm =
                        hiltViewModel<CloneProgressViewModel, CloneProgressViewModel.Factory>(
                            creationCallback = { factory -> factory.create(it.remoteUrl) },
                        )
                    CloneProgressScreen(vm) {
                        backStack.clear()
                        backStack.add(EntryBrowser)
                    }
                }

                entry<SessionStart> {
                    val vm: SessionStartViewModel = hiltViewModel()
                    val returnEntryPath = it.returnEntryPath
                    SessionStartScreen(
                        viewModel = vm,
                        onSuccess = {
                            backStack.removeLastOrNull()
                            if (returnEntryPath != null) {
                                backStack.add(EntryDetail(returnEntryPath))
                            }
                        },
                    )
                }

                entry<EntryBrowser> {
                    val navVm: NavViewModel = hiltViewModel()
                    val vm: EntryBrowserViewModel = hiltViewModel()
                    EntryBrowserScreen(
                        viewModel = vm,
                        onNavigateToEntryDetail = { entry ->
                            if (navVm.requiresSessionStart()) {
                                backStack.add(SessionStart(returnEntryPath = entry.path))
                            } else {
                                backStack.add(EntryDetail(entry.path))
                            }
                        },
                        onNavigateToSettings = { backStack.add(Settings) },
                    )
                }

                entry<EntryDetail> {
                    val navVm: NavViewModel = hiltViewModel()
                    val context = LocalContext.current
                    val maybeEntry = remember(it.entryPath) { navVm.findEntry(it.entryPath) }
                    if (maybeEntry == null) {
                        LaunchedEffect(Unit) {
                            Toast.makeText(context, "entry not found", Toast.LENGTH_SHORT).show()
                            backStack.removeLastOrNull()
                        }
                        return@entry
                    }
                    val vm =
                        hiltViewModel<EntryDetailViewModel, EntryDetailViewModel.Factory>(
                            creationCallback = { factory -> factory.create(maybeEntry) },
                        )
                    EntryDetailScreen(
                        viewModel = vm,
                        onBack = { backStack.removeLastOrNull() },
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
                            backStack.add(Welcome)
                        },
                    )
                }
            },
    )
}
