// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.zangetsu101.pass.autofill.AutofillHistory
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.keymanagement.KeyManagement
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.IOException
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

    private lateinit var storeScope: CoroutineScope
    private lateinit var autofillHistory: AutofillHistory

    private val card = PassEntry("cards/visa/daily", "visa", "daily", File("cards/visa/daily.gpg"), isCard = true)
    private val lastUsedCard = PassEntry("cards/amex/travel", "amex", "travel", File("cards/amex/travel.gpg"), isCard = true)
    private val cards = listOf(card, lastUsedCard)

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
        storeScope = CoroutineScope(testDispatcher + SupervisorJob())
        autofillHistory =
            AutofillHistory(
                PreferenceDataStoreFactory.create(
                    scope = storeScope,
                    produceFile = { File(tempDir, "autofill_history.preferences_pb") },
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(autofillHistory: AutofillHistory = this.autofillHistory) =
        SettingsViewModel(
            context = context,
            sessionOperations = sessionOperations,
            keyManagement = keyManagement,
            gpgKeyOperations = gpgKeyOperations,
            appPreferences = appPreferences,
            gitSync = gitSync,
            autofillHistory = autofillHistory,
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

            advanceUntilIoWorkIsDone()
            assertFalse(vm.state.value.clearing)
            assertTrue(completed)
        }

    @Test
    fun `clearAllData clears the autofill history`() =
        runTest(testDispatcher) {
            every { keyManagement.clearAllKeys() } just Runs
            autofillHistory.record("shop.example.com", lastUsedCard)
            assertEquals(listOf(lastUsedCard, card), autofillHistory.rank("shop.example.com", cards))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.clearAllData {}
            advanceUntilIoWorkIsDone()

            assertEquals(cards, autofillHistory.rank("shop.example.com", cards))
        }

    @Test
    fun `clearAllData completes when the autofill history cannot be cleared`() =
        runTest(testDispatcher) {
            every { keyManagement.clearAllKeys() } just Runs
            val vm = createViewModel(AutofillHistory(FailingWriteStore()))
            advanceUntilIdle()

            var completed = false
            vm.clearAllData { completed = true }
            advanceUntilIoWorkIsDone()

            assertFalse(vm.state.value.clearing)
            assertTrue(completed)
        }

    @Test
    fun `clearAutofillHistory clears the autofill history and reports success`() =
        runTest(testDispatcher) {
            autofillHistory.record("shop.example.com", lastUsedCard)
            assertEquals(listOf(lastUsedCard, card), autofillHistory.rank("shop.example.com", cards))
            val vm = createViewModel()

            var cleared: Boolean? = null
            vm.clearAutofillHistory { cleared = it }
            advanceUntilIdle()

            assertEquals(true, cleared)
            assertEquals(cards, autofillHistory.rank("shop.example.com", cards))
        }

    @Test
    fun `clearAutofillHistory reports a failure when the autofill history cannot be cleared`() =
        runTest(testDispatcher) {
            val vm = createViewModel(AutofillHistory(FailingWriteStore()))

            var cleared: Boolean? = null
            vm.clearAutofillHistory { cleared = it }
            advanceUntilIdle()

            assertEquals(false, cleared)
        }

    /**
     * Runs the work that goes through `withContext(Dispatchers.IO)`.
     * The first advance runs the coroutine until it moves to the IO thread.
     * The sleep gives the IO thread time to finish and to post the continuation to the test dispatcher.
     * The second advance runs that continuation.
     */
    private fun TestScope.advanceUntilIoWorkIsDone() {
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
    }

    private class FailingWriteStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = emptyFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("read-only store")
    }
}
