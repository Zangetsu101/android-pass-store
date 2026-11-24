// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.zangetsu101.pass.generator.CharacterSet
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.passstore.PasswordSource
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class NewEntryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val writer = mockk<LoginEntryWriter>()
    private val preferences = mockk<AppPreferences>()
    private val path = EntryPath("dev/github.com")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferences.clipboardTimeoutSeconds } returns flowOf(30)
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboard
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText(any(), any()) } returns mockk(relaxed = true)
        mockkObject(ClipboardClearReceiver.Companion)
        every { ClipboardClearReceiver.cancel(any()) } just Runs
        every { ClipboardClearReceiver.schedule(any(), any()) } just Runs
        coEvery { writer.insert(any(), any(), any(), any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ClipData::class)
        unmockkObject(ClipboardClearReceiver.Companion)
    }

    private fun createViewModel() = NewEntryViewModel(context, writer, mockk<PassStore>(relaxed = true), preferences)

    @Test
    fun `the form opens with a default generated password and copies it after the save`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val password = vm.password
            assertEquals(GeneratorSettings(), vm.passwordDraft.settings)
            assertEquals(25, password.length)
            assertTrue(vm.copyAfterSave)
            assertFalse(vm.passwordChanged)

            vm.save(path, expectedDigest = null)
            runCurrent()

            coVerify { writer.insert(path, null, password, "", PasswordSource.Generated) }
            verify { ClipData.newPlainText("password", password) }
            verify { ClipboardClearReceiver.schedule(context, 30_000L) }
            assertEquals("dev/github.com", vm.savedPath)
        }

    @Test
    fun `a typed password saves as given and the copy toggle can turn the copy off`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.updatePassword("my own password")
            vm.toggleCopyAfterSave()

            vm.save(path, expectedDigest = null)
            runCurrent()

            coVerify { writer.insert(path, null, "myownpassword", "", PasswordSource.Given) }
            verify(exactly = 0) { clipboard.setPrimaryClip(any()) }
        }

    @Test
    fun `the password changes only when it differs from the first password`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val first = vm.password
            vm.copyPassword()
            vm.toggleCopyAfterSave()
            assertFalse(vm.passwordChanged)

            vm.updatePassword("typed")
            assertFalse(vm.passwordDraft.generated)
            assertTrue(vm.passwordChanged)
            vm.updatePassword(first)
            assertFalse(vm.passwordChanged)

            vm.regenerate()
            assertTrue(vm.passwordDraft.generated)
            assertEquals(25, vm.password.length)
            assertTrue(vm.passwordChanged)
        }

    @Test
    fun `a change in the settings makes a new password`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.updateGenerator(
                vm.passwordDraft.settings
                    .withLength(32)
                    .toggle(CharacterSet.Symbols),
            )

            assertTrue(vm.passwordDraft.generated)
            assertTrue(vm.passwordChanged)
            assertEquals(32, vm.password.length)
            assertTrue(vm.password.all { it.isLetterOrDigit() })
        }
}
