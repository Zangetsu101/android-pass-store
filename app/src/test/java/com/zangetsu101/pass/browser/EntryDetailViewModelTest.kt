// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.keymanagement.session.BiometricAuthException
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val mockDecryption = mockk<Decryption>()
    private val mockGitSync = mockk<GitSync>()
    private val mockSessionOperations = mockk<SessionOperations>()
    private val mockAppPreferences = mockk<AppPreferences>()
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockActivity = mockk<FragmentActivity>(relaxed = true)

    private val fakeEntry =
        PassEntry(
            path = "test/secret.gpg",
            domain = "example.com",
            username = "user@example.com",
            encryptedFile = File("test/secret.gpg"),
        )

    private val fakeCredentials =
        Credentials(
            password = "p4ssw0rd".toCharArray(),
            notes = "",
            username = "user@example.com",
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockGitSync.lastCommitForFile(any()) } returns null
        every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(45)
        every { mockSessionOperations.sessionState } returns MutableStateFlow(SessionState.Active)
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        mockkObject(ClipboardClearReceiver.Companion)
        every { ClipboardClearReceiver.cancel(any()) } just Runs
        every { ClipboardClearReceiver.schedule(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ClipData::class)
        unmockkObject(ClipboardClearReceiver.Companion)
    }

    private fun createViewModel() =
        EntryDetailViewModel(
            entry = fakeEntry,
            context = mockContext,
            decryption = mockDecryption,
            sessionOperations = mockSessionOperations,
            gitSync = mockGitSync,
            appPreferences = mockAppPreferences,
        )

    @Test
    fun `init - lastCommitForFile returns commit info sets GitStatus Tracked`() =
        runTest(testDispatcher) {
            val commitInfo =
                FileCommitInfo(
                    commitHash = "abc123",
                    commitTime = Instant.parse("2024-01-01T00:00:00Z"),
                )
            coEvery { mockGitSync.lastCommitForFile(fakeEntry.path) } returns commitInfo
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(GitStatus.Tracked(commitInfo), viewModel.state.value.gitStatus)
        }

    @Test
    fun `init - lastCommitForFile returns null sets GitStatus Untracked`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(GitStatus.Untracked, viewModel.state.value.gitStatus)
        }

    @Test
    fun `init - clipboardTimeoutSeconds pref value reflected in state`() =
        runTest(testDispatcher) {
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(30)
            val viewModel = createViewModel()

            advanceUntilIdle()

            assertEquals(30, viewModel.state.value.clipboardTimeoutSeconds)
        }

    @Test
    fun `authenticate when unlockState is Decrypting is no-op`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            advanceUntilIdle()

            // First call sets Decrypting; coroutine body not yet run (StandardTestDispatcher)
            viewModel.authenticate(mockActivity)
            assertEquals(UnlockState.Decrypting, viewModel.state.value.unlockState)

            // Second call — guard triggers, no-op
            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `authenticate success sets unlockState to Decrypted`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            assertEquals(UnlockState.Decrypted(fakeCredentials), viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate BiometricAuthException resets unlockState to Idle`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } throws BiometricAuthException("cancelled")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate SessionError NoActiveSession sets unlockState to Failed with session message`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } throws SessionError.NoActiveSession()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            assertEquals(
                UnlockState.Failed("Session expired — return to home and unlock"),
                viewModel.state.value.unlockState,
            )
        }

    @Test
    fun `authenticate DecryptionError sets unlockState to Failed with error message`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } throws DecryptionError("Bad key")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            assertEquals(UnlockState.Failed("Bad key"), viewModel.state.value.unlockState)
        }

    @Test
    fun `toggleReveal when Decrypted toggles passwordRevealed`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            assertFalse((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)

            viewModel.toggleReveal()
            assertTrue((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)

            viewModel.toggleReveal()
            assertFalse((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)
        }

    @Test
    fun `copyPassword sets clipboardCopied true then clears after timeout`() =
        runTest(testDispatcher) {
            val timeoutSeconds = 45
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(timeoutSeconds)
            val mockClipboard = mockk<ClipboardManager>(relaxed = true)
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboard
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials

            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.authenticate(mockActivity)
            advanceUntilIdle()

            viewModel.copyPassword()

            assertTrue((viewModel.state.value.unlockState as UnlockState.Decrypted).clipboardCopied)

            advanceTimeBy(delayTimeMillis = timeoutSeconds * 1000L + 1)
            runCurrent()

            assertFalse((viewModel.state.value.unlockState as UnlockState.Decrypted).clipboardCopied)
        }
}
