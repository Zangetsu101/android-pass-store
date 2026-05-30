package com.zangetsu101.pass.settings

import android.content.Context
import app.cash.turbine.test
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.keymanagement.KeyManagement
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val sessionOperations = mockk<SessionOperations>(relaxed = true)
    private val keyManagement = mockk<KeyManagement>()
    private val gpgKeyOperations = mockk<GpgKeyStore>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val gitSync = mockk<GitSync>()
    private val context = mockk<Context>(relaxed = true)

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { gitSync.syncStatus() } returns SyncStatus(null, null, false)
        every { gpgKeyOperations.getGpgKeyInfo() } returns null
        every { sessionOperations.sessionState } returns MutableStateFlow(SessionState.Inactive(EndReason.MANUAL))
        every { appPreferences.sshPublicKey } returns flowOf(null)
        every { appPreferences.remoteUrl } returns flowOf("")
        every { appPreferences.sessionTimeoutMinutes } returns flowOf(5)
        every { appPreferences.clipboardTimeoutSeconds } returns flowOf(45)
        every { appPreferences.defaultViewTree } returns flowOf(true)
        every { context.filesDir } returns tempDir
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(
            context = context,
            sessionOperations = sessionOperations,
            keyManagement = keyManagement,
            gpgKeyOperations = gpgKeyOperations,
            appPreferences = appPreferences,
            gitSync = gitSync,
        )

    @Test
    fun `sessionActive reflects session state changes`() =
        runTest(testDispatcher) {
            val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Inactive(EndReason.MANUAL))
            every { sessionOperations.sessionState } returns sessionStateFlow
            val vm = createViewModel()

            vm.sessionActive.test {
                assertFalse(awaitItem())
                sessionStateFlow.value = SessionState.Active
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearAllData transitions clearing state and calls onComplete`() =
        runTest(testDispatcher) {
            every { keyManagement.clearAllKeys() } just Runs
            val vm = createViewModel()
            advanceUntilIdle()

            var completed = false
            vm.clearAllData { completed = true }
            assertTrue(vm.state.value.clearing)

            // First advance runs the launched coroutine up to withContext(Dispatchers.IO),
            // at which point it suspends and the IO thread starts. Sleep gives the IO thread
            // time to finish and post its continuation back to testDispatcher, then the
            // second advance processes that continuation.
            advanceUntilIdle()
            Thread.sleep(100)
            advanceUntilIdle()
            assertFalse(vm.state.value.clearing)
            assertTrue(completed)
        }
}
