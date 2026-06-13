package com.zangetsu101.pass.navigation

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
import com.zangetsu101.pass.browser.EntryBrowserScreen
import com.zangetsu101.pass.browser.EntryBrowserViewModel
import com.zangetsu101.pass.browser.EntryDetailScreen
import com.zangetsu101.pass.browser.EntryDetailViewModel
import com.zangetsu101.pass.onboarding.CloneProgressScreen
import com.zangetsu101.pass.onboarding.CloneProgressViewModel
import com.zangetsu101.pass.onboarding.CloneRepoScreen
import com.zangetsu101.pass.onboarding.CloneRepoViewModel
import com.zangetsu101.pass.onboarding.GpgImportViewModel
import com.zangetsu101.pass.onboarding.OnboardingGpgImportScreen
import com.zangetsu101.pass.onboarding.WelcomeScreen
import com.zangetsu101.pass.onboarding.WelcomeViewModel
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.session.SessionStartScreen
import com.zangetsu101.pass.session.SessionStartViewModel
import com.zangetsu101.pass.settings.SettingsScreen
import com.zangetsu101.pass.settings.SettingsViewModel
import com.zangetsu101.pass.syncpanel.SyncPanelScreen
import com.zangetsu101.pass.syncpanel.SyncPanelViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable data object Splash : NavKey

@Serializable data object Welcome : NavKey

@Serializable data object GpgImport : NavKey

@Serializable data object CloneRepo : NavKey

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
fun PassAndroidNavHost(appPreferences: AppPreferences) {
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
                    val vm: WelcomeViewModel = hiltViewModel()
                    WelcomeScreen(vm) { backStack.add(GpgImport) }
                }

                entry<GpgImport> {
                    val vm: GpgImportViewModel = hiltViewModel()
                    OnboardingGpgImportScreen(vm) {
                        backStack.add(CloneRepo)
                    }
                }

                entry<CloneRepo> {
                    val vm: CloneRepoViewModel = hiltViewModel()
                    CloneRepoScreen(vm) { url ->
                        backStack.add(OnboardingClone(url))
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
