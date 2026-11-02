// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
            notes = "private notes\ncvv: 123",
            username = "user@example.com",
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockGitSync.lastCommitForFile(any()) } returns null
        every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(45)
        every { mockSessionOperations.sessionState } returns MutableStateFlow(SessionState.Active)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { testDispatcher.scheduler.currentTime }
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        mockkObject(ClipboardClearReceiver.Companion)
        every { ClipboardClearReceiver.cancel(any()) } just Runs
        every { ClipboardClearReceiver.schedule(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
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
    fun `configuration recreation preserves details and original deadline`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.toggleReveal()
            advanceTimeBy(50_000)
            viewModel.onScreenDisposed(changingConfigurations = true)
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            assertTrue((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `recreation after original deadline stays locked until explicit decrypt`() =
        runTest(testDispatcher) {
            var elapsed = 0L
            every { SystemClock.elapsedRealtime() } answers { elapsed }
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = true)
            elapsed = 65_000L
            viewModel.refreshLock()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }

            viewModel.authenticate(mockActivity)
            runCurrent()
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            coVerify(exactly = 2) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `navigation disposal still locks immediately without automatic reauthentication`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = false)
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `recreation during decryption keeps the original operation`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } coAnswers {
                delay(1_000)
                fakeCredentials
            }
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = true)
            viewModel.onScreenEntered(mockActivity)
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `entry locks sixty seconds after decryption without ending session`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            advanceTimeBy(59_999)
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            assertEquals(SessionState.Active, mockSessionOperations.sessionState.value)
        }

    @Test
    fun `reveal and copy never extend the original deadline`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockk<ClipboardManager>(relaxed = true)
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            advanceTimeBy(50_000)
            viewModel.toggleReveal()
            viewModel.copyPassword()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `return before deadline retains details but return after sleep locks synchronously`() =
        runTest(testDispatcher) {
            var elapsed = 0L
            every { SystemClock.elapsedRealtime() } answers { elapsed }
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            elapsed = 50_000L
            viewModel.refreshLock()
            assertEquals(UnlockState.Decrypted(fakeCredentials), viewModel.state.value.unlockState)
            elapsed = 60_000L
            viewModel.refreshLock()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `expired details cannot be revealed or copied before timer resumes`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            every { SystemClock.elapsedRealtime() } returns 60_000L
            viewModel.copyPassword()
            viewModel.toggleReveal()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            verify(exactly = 0) { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) }
        }

    @Test
    fun `navigation locks immediately and a subsequent decrypt gets a new deadline`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            advanceTimeBy(10_000)
            viewModel.lock()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            viewModel.authenticate(mockActivity)
            runCurrent()
            advanceTimeBy(50_000)
            runCurrent()
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `navigation during decryption cannot repopulate details`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } coAnswers {
                withContext(NonCancellable) {
                    delay(1_000)
                    fakeCredentials
                }
            }
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            viewModel.lock()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `deadline starts on successful decryption not authentication start`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } coAnswers {
                delay(10_000)
                fakeCredentials
            }
            val viewModel = createViewModel()
            viewModel.authenticate(mockActivity)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            advanceTimeBy(59_999)
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

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

            runCurrent()

            assertEquals(GitStatus.Tracked(commitInfo), viewModel.state.value.gitStatus)
        }

    @Test
    fun `init - lastCommitForFile returns null sets GitStatus Untracked`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()

            runCurrent()

            assertEquals(GitStatus.Untracked, viewModel.state.value.gitStatus)
        }

    @Test
    fun `init - clipboardTimeoutSeconds pref value reflected in state`() =
        runTest(testDispatcher) {
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(30)
            val viewModel = createViewModel()

            runCurrent()

            assertEquals(30, viewModel.state.value.clipboardTimeoutSeconds)
        }

    @Test
    fun `authenticate when unlockState is Decrypting is no-op`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            runCurrent()

            // First call sets Decrypting; coroutine body not yet run (StandardTestDispatcher)
            viewModel.authenticate(mockActivity)
            assertEquals(UnlockState.Decrypting, viewModel.state.value.unlockState)

            // Second call — guard triggers, no-op
            viewModel.authenticate(mockActivity)
            runCurrent()

            coVerify(exactly = 1) { mockDecryption.decrypt(any(), any()) }
        }

    @Test
    fun `authenticate success sets unlockState to Decrypted`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Decrypted(fakeCredentials), viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate BiometricAuthException resets unlockState to Idle`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } throws BiometricAuthException("cancelled")
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate SessionError NoActiveSession sets unlockState to Failed with session message`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } throws SessionError.NoActiveSession()
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

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
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Failed("Bad key"), viewModel.state.value.unlockState)
        }

    @Test
    fun `toggleReveal when Decrypted toggles passwordRevealed`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials
            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            assertFalse((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)

            viewModel.toggleReveal()
            assertTrue((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)

            viewModel.toggleReveal()
            assertFalse((viewModel.state.value.unlockState as UnlockState.Decrypted).passwordRevealed)
        }

    @Test
    fun `copyPassword keeps clipboard alarm independent of entry expiry`() =
        runTest(testDispatcher) {
            val timeoutSeconds = 90
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(timeoutSeconds)
            val mockClipboard = mockk<ClipboardManager>(relaxed = true)
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockClipboard
            coEvery { mockDecryption.decrypt(any(), any()) } returns fakeCredentials

            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            viewModel.copyPassword()

            assertTrue((viewModel.state.value.unlockState as UnlockState.Decrypted).clipboardCopied)

            advanceTimeBy(delayTimeMillis = timeoutSeconds * 1000L + 1)
            runCurrent()

            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            verify(exactly = 1) { ClipboardClearReceiver.schedule(mockContext, 90_000L) }
            verify(exactly = 1) { ClipboardClearReceiver.cancel(mockContext) }
        }
}
