// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.DecryptedEntry
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionError
import com.zangetsu101.pass.decryption.RawEntry
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.keymanagement.session.BiometricAuthException
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PasswordSource
import com.zangetsu101.pass.passstore.SavedLogin
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
    private val mockWriter = mockk<LoginEntryWriter>()
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

    private fun givenOtpEntry() {
        val otp =
            com.zangetsu101.pass.otp
                .parseOtp("otpauth://totp/test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials.copy(otp = otp))
    }

    @Test
    fun `the header name is the file name before decryption and the Username Line after it`() {
        val credentials = fakeCredentials.copy(username = "alice")

        assertEquals("user@example.com", UnlockState.Idle.headerName(fakeEntry))
        assertEquals("alice", UnlockState.Decrypted(credentials).headerName(fakeEntry))
    }

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

    @Test
    fun `OTP copy is sensitive uses settings and resets hint without extending lock`() =
        runTest {
            givenOtpEntry()
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(12)
            val clipboard = mockk<ClipboardManager>(relaxed = true)
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboard
            val vm = createViewModel()
            vm.authenticate(mockActivity)
            runCurrent()
            vm.copyOtp()
            verify { ClipData.newPlainText("otp", match { it.toString().matches(Regex("[0-9]{6}")) }) }
            verify { clipboard.setPrimaryClip(any()) }
            verify { ClipboardClearReceiver.schedule(mockContext, 12000L) }
            assertEquals(Copied.OtpCode, (vm.state.value.unlockState as UnlockState.Decrypted).copied)
            advanceTimeBy(1501)
            runCurrent()
            assertEquals(null, (vm.state.value.unlockState as UnlockState.Decrypted).copied)
            advanceTimeBy(58500)
            runCurrent()
            assertTrue(vm.state.value.unlockState is UnlockState.Idle)
            vm.copyOtp()
            verify(exactly = 1) { clipboard.setPrimaryClip(any()) }
        }

    @Test
    fun `an open draft can be saved after the session expires`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Active)
            every { mockSessionOperations.sessionState } returns session
            val rawEntry = RawEntry("old password\nnotes", byteArrayOf(1, 2, 3))
            val original = DecryptedEntry(fakeCredentials, rawEntry)
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns original
            val saved = fakeCredentials.copy(password = "newpassword".toCharArray(), notes = "notes")
            val commit = FileCommitInfo("abc1234", Instant.EPOCH)
            coEvery {
                mockWriter.save(
                    fakeEntry,
                    match {
                        it.ciphertextDigest.contentEquals(rawEntry.ciphertextDigest)
                    },
                    "newpassword",
                    "notes",
                    PasswordSource.Given,
                )
            } returns
                SavedLogin(DecryptedEntry(saved, RawEntry("newpassword\nnotes", byteArrayOf(4))), commit)
            val vm = createViewModel()

            vm.authenticate(mockActivity)
            runCurrent()
            assertEquals(EntryDetailMode.ViewEntry, vm.state.value.mode)
            vm.startEditing()
            vm.updateEditorPassword("new password\t")
            session.value = SessionState.Inactive(EndReason.TIMEOUT)
            runCurrent()
            assertEquals(
                "newpassword",
                (vm.state.value.mode as EntryDetailMode.EditEntry).draft.password,
            )

            vm.saveEditor()
            runCurrent()

            assertEquals(EntryDetailMode.ViewEntry, vm.state.value.mode)
            assertEquals(saved, (vm.state.value.unlockState as UnlockState.Decrypted).credentials)
            assertEquals(GitStatus.Tracked(commit), vm.state.value.gitStatus)
            vm.startEditing()
            assertEquals(EntryDetailMode.ViewEntry, vm.state.value.mode)
        }

    @Test
    fun `a regenerate in the editor saves with the generated source until the user types`() =
        runTest {
            val rawEntry = RawEntry("old password\nnotes", byteArrayOf(1))
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials, rawEntry)
            val saved = SavedLogin(DecryptedEntry(fakeCredentials, rawEntry), FileCommitInfo("abc1234", Instant.EPOCH))
            coEvery { mockWriter.save(fakeEntry, any(), any(), "notes", any()) } returns saved
            val vm = createViewModel()
            vm.authenticate(mockActivity)
            runCurrent()
            vm.startEditing()

            fun draft() = (vm.state.value.mode as EntryDetailMode.EditEntry).draft

            vm.updateEditorGenerator(draft().passwordDraft.settings.withLength(32))
            val generated = draft().password
            assertEquals(32, generated.length)
            assertTrue(draft().passwordDraft.generated)
            assertEquals(PasswordSource.Generated, draft().passwordSource)

            vm.updateEditorPassword(generated + "x")
            assertFalse(draft().passwordDraft.generated)
            assertEquals(PasswordSource.Given, draft().passwordSource)

            vm.regenerateEditorPassword()
            vm.updateEditorNotes("new notes")
            assertEquals(PasswordSource.Given, draft().passwordSource)
            vm.updateEditorNotes("notes")
            assertEquals(32, draft().password.length)
            val regenerated = draft().password
            vm.saveEditor()
            runCurrent()

            coVerify { mockWriter.save(fakeEntry, any(), regenerated, "notes", PasswordSource.Generated) }
        }

    @Test
    fun `the editor requires a password when the original login has one`() =
        runTest {
            val rawEntry = RawEntry("password", byteArrayOf())
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials, rawEntry)
            val vm = createViewModel()
            vm.authenticate(mockActivity)
            runCurrent()
            vm.startEditing()
            assertFalse(
                (vm.state.value.mode as EntryDetailMode.EditEntry).draft.changed,
            )

            vm.updateEditorPassword("  \t")
            vm.saveEditor()

            assertEquals(
                "password cannot be empty",
                (vm.state.value.mode as EntryDetailMode.EditEntry).draft.error,
            )
            coVerify(exactly = 0) { mockWriter.save(any(), any(), any(), any(), any()) }
            vm.discardEditor()
            assertEquals(EntryDetailMode.ViewEntry, vm.state.value.mode)
        }

    @Test
    fun `OTP only drafts can save notes with an empty password and still lock on later expiry`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Active)
            every { mockSessionOperations.sessionState } returns session
            val rawEntry = RawEntry("otpauth://invalid", byteArrayOf())
            val original = fakeCredentials.copy(password = charArrayOf(), notes = "")
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(original, rawEntry)
            val saved = original.copy(notes = "new notes")
            coEvery { mockWriter.save(fakeEntry, any(), "", "new notes", PasswordSource.Given) } returns
                SavedLogin(
                    DecryptedEntry(saved, RawEntry("otpauth://invalid\nnew notes", byteArrayOf(4))),
                    FileCommitInfo("abc1234", Instant.EPOCH),
                )
            val vm = createViewModel()
            vm.authenticate(mockActivity)
            runCurrent()
            vm.startEditing()
            vm.updateEditorNotes("new notes")
            vm.saveEditor()
            runCurrent()
            assertEquals(saved, (vm.state.value.unlockState as UnlockState.Decrypted).credentials)
            assertEquals(EntryDetailMode.ViewEntry, vm.state.value.mode)

            vm.startEditing()
            val reopened = (vm.state.value.mode as EntryDetailMode.EditEntry).draft
            assertEquals("new notes", reopened.notes)
            assertTrue(reopened.hasOtp)
            assertFalse(reopened.changed)
            vm.discardEditor()

            session.value = SessionState.Inactive(EndReason.TIMEOUT)
            runCurrent()

            assertTrue(vm.state.value.unlockState is UnlockState.Failed)
        }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
        unmockkStatic(ClipData::class)
        unmockkObject(ClipboardClearReceiver.Companion)
    }

    private val cardEntry = PassEntry("cards/visa.gpg", null, "visa", File("cards/visa.gpg"), isCard = true)

    private fun TestScope.editCard(plaintext: String): EntryDetailViewModel {
        val rawEntry = RawEntry(plaintext, byteArrayOf(1))
        val decrypted = DecryptedEntry(fakeCredentials, rawEntry)
        coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns decrypted
        coEvery { mockWriter.save(any(), any(), any(), any(), any()) } returns
            SavedLogin(decrypted, FileCommitInfo("abc1234", Instant.EPOCH))
        val vm = createViewModel(cardEntry)
        vm.authenticate(mockActivity)
        runCurrent()
        vm.startEditing()
        return vm
    }

    private val EntryDetailViewModel.draft get() = (state.value.mode as EntryDetailMode.EditEntry).draft

    @Test
    fun `an unchanged card number is written back exactly as it was`() =
        runTest {
            val vm = editCard("4111 1111 1111 1111\ncvv: 123")
            assertEquals(EntryType.Card, vm.draft.type)
            assertEquals("4111111111111111", vm.draft.password)

            vm.updateEditorCardNumber("4111-1111-1111-1111")
            assertFalse(vm.draft.changed)
            vm.updateEditorNotes("cvv: 456")
            vm.saveEditor()
            runCurrent()

            coVerify { mockWriter.save(cardEntry, any(), "4111 1111 1111 1111", "cvv: 456", PasswordSource.Given) }
        }

    @Test
    fun `a changed card number is saved as digits only`() =
        runTest {
            val vm = editCard("4111 1111 1111 1111\ncvv: 123")

            vm.updateEditorCardNumber("4242 4242 4242 4242")
            assertTrue(vm.draft.changed)
            vm.saveEditor()
            runCurrent()

            coVerify { mockWriter.save(cardEntry, any(), "4242424242424242", "cvv: 123", PasswordSource.Given) }
        }

    @Test
    fun `the card editor requires a card number`() =
        runTest {
            val vm = editCard("4111111111111111")

            vm.updateEditorCardNumber("")
            vm.saveEditor()

            assertEquals("card number cannot be empty", vm.draft.error)
            coVerify(exactly = 0) { mockWriter.save(any(), any(), any(), any(), any()) }
        }

    private fun createViewModel(entry: PassEntry = fakeEntry) =
        EntryDetailViewModel(
            entry = entry,
            context = mockContext,
            decryption = mockDecryption,
            sessionOperations = mockSessionOperations,
            gitSync = mockGitSync,
            appPreferences = mockAppPreferences,
            loginWriter = mockWriter,
        )

    @Test
    fun `stale collected details are rejected at expiry and after synchronous lock`() =
        runTest(testDispatcher) {
            var elapsed = 0L
            every { SystemClock.elapsedRealtime() } answers { elapsed }
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            assertFalse(viewModel.hasUnexpiredDetails())
            viewModel.authenticate(mockActivity)
            runCurrent()
            val collected = viewModel.state.value
            elapsed = 59_999L
            assertTrue(viewModel.hasUnexpiredDetails())
            elapsed = 60_000L
            assertFalse(viewModel.hasUnexpiredDetails())
            // The guard is read-only even before the timer or resume callback runs.
            assertEquals(collected, viewModel.state.value)
            viewModel.refreshLock()
            assertTrue(collected.unlockState is UnlockState.Decrypted)
            assertFalse(viewModel.hasUnexpiredDetails())
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)

            viewModel.authenticate(mockActivity)
            runCurrent()
            assertTrue(viewModel.hasUnexpiredDetails())
            viewModel.lock()
            assertFalse(viewModel.hasUnexpiredDetails())
            assertEquals(SessionState.Active, mockSessionOperations.sessionState.value)
        }

    @Test
    fun `configuration recreation preserves details and original deadline`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coVerify(exactly = 1) { mockDecryption.decryptForEntryDetail(any(), any()) }
        }

    @Test
    fun `recreation after original deadline stays locked until explicit decrypt`() =
        runTest(testDispatcher) {
            var elapsed = 0L
            every { SystemClock.elapsedRealtime() } answers { elapsed }
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = true)
            elapsed = 65_000L
            viewModel.refreshLock()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            coVerify(exactly = 1) { mockDecryption.decryptForEntryDetail(any(), any()) }

            viewModel.authenticate(mockActivity)
            runCurrent()
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            coVerify(exactly = 2) { mockDecryption.decryptForEntryDetail(any(), any()) }
        }

    @Test
    fun `navigation disposal still locks immediately without automatic reauthentication`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = false)
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            coVerify(exactly = 1) { mockDecryption.decryptForEntryDetail(any(), any()) }
        }

    @Test
    fun `recreation during decryption keeps the original operation`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } coAnswers {
                delay(1_000)
                DecryptedEntry(fakeCredentials)
            }
            val viewModel = createViewModel()
            viewModel.onScreenEntered(mockActivity)
            runCurrent()
            viewModel.onScreenDisposed(changingConfigurations = true)
            viewModel.onScreenEntered(mockActivity)
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(viewModel.state.value.unlockState is UnlockState.Decrypted)
            coVerify(exactly = 1) { mockDecryption.decryptForEntryDetail(any(), any()) }
        }

    @Test
    fun `entry locks sixty seconds after decryption without ending session`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } coAnswers {
                withContext(NonCancellable) {
                    delay(1_000)
                    DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } coAnswers {
                delay(10_000)
                DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            runCurrent()

            // First call sets Decrypting; coroutine body not yet run (StandardTestDispatcher)
            viewModel.authenticate(mockActivity)
            assertEquals(UnlockState.Decrypting, viewModel.state.value.unlockState)

            // Second call — guard triggers, no-op
            viewModel.authenticate(mockActivity)
            runCurrent()

            coVerify(exactly = 1) { mockDecryption.decryptForEntryDetail(any(), any()) }
        }

    @Test
    fun `authenticate success sets unlockState to Decrypted`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Decrypted(fakeCredentials), viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate BiometricAuthException resets unlockState to Idle`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } throws BiometricAuthException("cancelled")
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
        }

    @Test
    fun `authenticate SessionError NoActiveSession sets unlockState to Failed with session message`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } throws SessionError.NoActiveSession()
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } throws DecryptionError("Bad key")
            val viewModel = createViewModel()
            runCurrent()

            viewModel.authenticate(mockActivity)
            runCurrent()

            assertEquals(UnlockState.Failed("Bad key"), viewModel.state.value.unlockState)
        }

    @Test
    fun `toggleReveal when Decrypted toggles passwordRevealed`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
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
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)

            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            viewModel.copyPassword()

            assertEquals(Copied.Password, (viewModel.state.value.unlockState as UnlockState.Decrypted).copied)

            advanceTimeBy(delayTimeMillis = timeoutSeconds * 1000L + 1)
            runCurrent()

            assertEquals(UnlockState.Idle, viewModel.state.value.unlockState)
            verify(exactly = 1) { ClipboardClearReceiver.schedule(mockContext, 90_000L) }
            verify(exactly = 1) { ClipboardClearReceiver.cancel(mockContext) }
        }

    @Test
    fun `a field copy is sensitive and takes the copied mark`() =
        runTest(testDispatcher) {
            every { mockAppPreferences.clipboardTimeoutSeconds } returns flowOf(12)
            val clipboard = mockk<ClipboardManager>(relaxed = true)
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboard
            val credentials = fakeCredentials.copy(notes = "note\nusername: alice\npin: 4821")
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(credentials)
            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()
            viewModel.copyPassword()

            viewModel.copyField(1)

            verify { ClipData.newPlainText("pin", "4821") }
            verify(exactly = 2) { clipboard.setPrimaryClip(any()) }
            verify { ClipboardClearReceiver.schedule(mockContext, 12_000L) }
            assertEquals(Copied.Field(1), (viewModel.state.value.unlockState as UnlockState.Decrypted).copied)

            viewModel.copyPassword()
            assertEquals(Copied.Password, (viewModel.state.value.unlockState as UnlockState.Decrypted).copied)

            viewModel.copyField(0)
            advanceTimeBy(12_001)
            runCurrent()
            assertEquals(null, (viewModel.state.value.unlockState as UnlockState.Decrypted).copied)
        }

    @Test
    fun `the end of the OTP hint keeps a later copied mark`() =
        runTest(testDispatcher) {
            givenOtpEntry()
            every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockk<ClipboardManager>(relaxed = true)
            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            viewModel.copyOtp()
            viewModel.copyPassword()
            advanceTimeBy(1501)
            runCurrent()

            assertEquals(Copied.Password, (viewModel.state.value.unlockState as UnlockState.Decrypted).copied)
        }

    @Test
    fun `a tap on a secret field shows its value and a second tap blurs it again`() =
        runTest(testDispatcher) {
            val credentials = fakeCredentials.copy(notes = "username: alice\npin: 4821\ncvv: 123")
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(credentials)
            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            viewModel.toggleFieldReveal(1)
            viewModel.toggleFieldReveal(2)
            assertEquals(setOf(1, 2), (viewModel.state.value.unlockState as UnlockState.Decrypted).revealedFields)

            viewModel.toggleFieldReveal(1)
            assertEquals(setOf(2), (viewModel.state.value.unlockState as UnlockState.Decrypted).revealedFields)
        }

    @Test
    fun `the decrypted state splits the notes into fields and lines`() =
        runTest(testDispatcher) {
            coEvery { mockDecryption.decryptForEntryDetail(any(), any()) } returns DecryptedEntry(fakeCredentials)
            val viewModel = createViewModel()
            runCurrent()
            viewModel.authenticate(mockActivity)
            runCurrent()

            val notes = (viewModel.state.value.unlockState as UnlockState.Decrypted).notes
            assertEquals(listOf("cvv"), notes.fields.map { it.key })
            assertEquals(listOf("private notes"), notes.lines)
        }
}
