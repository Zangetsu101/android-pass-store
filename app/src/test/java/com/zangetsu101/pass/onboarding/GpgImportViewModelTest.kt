package com.zangetsu101.pass.onboarding

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GpgImportViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val cryptoOperations = mockk<GpgKeyStore>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private lateinit var viewModel: GpgImportViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = GpgImportViewModel(cryptoOperations, appPreferences, testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setGpgKeyText updates gpgKeyText and clears error and imported flag`() {
        viewModel.setGpgKeyText("foo")

        val state = viewModel.state.value
        assertEquals("foo", state.gpgKeyText)
        assertNull(state.gpgImportError)
        assertFalse(state.gpgImported)
    }

    @Test
    fun `importGpgKey success sets gpgImported true and persists via appPreferences`() =
        runTest(testDispatcher) {
            every { cryptoOperations.importGpgKey(any()) } returns Unit
            every { cryptoOperations.findAuthSubkey() } returns null
            coEvery { appPreferences.setGpgImported(true) } just Awaits

            viewModel.importGpgKey()

            assertTrue(viewModel.state.value.gpgImported)
            assertNull(viewModel.state.value.gpgImportError)
            coVerify { appPreferences.setGpgImported(true) }
        }

    @Test
    fun `importGpgKey success populates authSubkey when auth subkey present`() =
        runTest(testDispatcher) {
            val fakeAuthSubkey =
                AuthSubkeyInfo(
                    keyId = 0xDEADBEEFL,
                    sshPublicKey = "ssh-ed25519 AAAAKEY",
                    sshFingerprint = "SHA256:abc",
                    uid = "Test <t@t.com>",
                    created = 1_000L,
                )
            every { cryptoOperations.importGpgKey(any()) } returns Unit
            every { cryptoOperations.findAuthSubkey() } returns fakeAuthSubkey

            viewModel.importGpgKey()

            assertEquals(fakeAuthSubkey, viewModel.state.value.authSubkey)
        }

    @Test
    fun `importGpgKey success leaves authSubkey null when no auth subkey`() =
        runTest(testDispatcher) {
            every { cryptoOperations.importGpgKey(any()) } returns Unit
            every { cryptoOperations.findAuthSubkey() } returns null

            viewModel.importGpgKey()

            assertNull(viewModel.state.value.authSubkey)
        }

    @Test
    fun `importGpgKey throws NoPassphrase sets gpgImportError and keeps gpgImported false`() =
        runTest(testDispatcher) {
            every { cryptoOperations.importGpgKey(any()) } throws KeyImportError.NoPassphrase()

            viewModel.importGpgKey()

            assertNotNull(viewModel.state.value.gpgImportError)
            assertFalse(viewModel.state.value.gpgImported)
        }

    @Test
    fun `importGpgKey throws Malformed sets gpgImportError`() =
        runTest(testDispatcher) {
            every { cryptoOperations.importGpgKey(any()) } throws KeyImportError.Malformed()

            viewModel.importGpgKey()

            assertNotNull(viewModel.state.value.gpgImportError)
        }
}
