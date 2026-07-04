// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.datastore.preferences.core.Preferences
import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bouncycastle.openpgp.PGPSecretKeyRing
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
    private val testDispatcher = StandardTestDispatcher()
    private val cryptoOperations = mockk<GpgKeyStore>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val candidate = GpgImportCandidate("key", mockk<PGPSecretKeyRing>())
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
    fun `setGpgKeyText updates gpgKeyText and clears error imported flag and modal`() {
        viewModel.importGpgKey()
        viewModel.setGpgKeyText("foo")

        val state = viewModel.state.value
        assertEquals("foo", state.gpgKeyText)
        assertNull(state.gpgImportError)
        assertFalse(state.gpgImported)
        assertNull(state.importModal)
    }

    @Test
    fun `importGpgKey success completes checklist and persists via appPreferences`() =
        runTest(testDispatcher) {
            stubSuccessfulImport(hasAuthSubkey = false)
            coEvery { appPreferences.setGpgImported(true) } returns mockk<Preferences>()

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.gpgImported)
            assertNull(state.gpgImportError)
            assertEquals(GpgImportModalPhase.SUCCESS, state.importModal?.phase)
            assertTrue(state.importModal!!.rows.dropLast(2).all { it.status == GpgImportStepStatus.PASSED })
            assertEquals(GpgImportStepStatus.NEUTRAL, state.importModal!!.rows.first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }.status)
            assertEquals(GpgImportStepStatus.PASSED, state.importModal!!.rows.first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }.status)
            coVerify { appPreferences.setGpgImported(true) }
        }

    @Test
    fun `importGpgKey with auth subkey marks optional row passed`() =
        runTest(testDispatcher) {
            stubSuccessfulImport(hasAuthSubkey = true)
            coEvery { appPreferences.setGpgImported(true) } returns mockk<Preferences>()

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            assertEquals(
                GpgImportStepStatus.PASSED,
                viewModel.state.value.importModal!!.rows.first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }.status,
            )
        }

    @Test
    fun `importGpgKey throws NoPassphrase fails passphrase row and keeps later rows not checked`() =
        runTest(testDispatcher) {
            every { cryptoOperations.parseGpgKeyImportCandidate(any()) } returns candidate
            every { cryptoOperations.requireEncryptionSubkey(candidate) } returns Unit
            every { cryptoOperations.requireValidEncryptionSubkey(candidate) } returns Unit
            every { cryptoOperations.requirePrivateEncryptionMaterial(candidate) } returns Unit
            every { cryptoOperations.requirePassphraseProtection(candidate) } throws KeyImportError.NoPassphrase()

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            val rows = viewModel.state.value.importModal!!.rows
            assertEquals(GpgImportModalPhase.FAILED, viewModel.state.value.importModal?.phase)
            assertEquals(GpgImportStepStatus.FAILED, rows.first { it.step == GpgImportStep.PASSPHRASE_PROTECTION }.status)
            assertEquals(GpgImportStepStatus.NOT_CHECKED, rows.first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }.status)
            assertEquals(GpgImportStepStatus.NOT_CHECKED, rows.first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }.status)
            assertFalse(viewModel.state.value.gpgImported)
        }

    @Test
    fun `cancel closes modal and ignores later result`() =
        runTest(testDispatcher) {
            every { cryptoOperations.parseGpgKeyImportCandidate(any()) } returns candidate

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            viewModel.cancelImport()
            advanceUntilIdle()

            assertNull(viewModel.state.value.importModal)
            assertFalse(viewModel.state.value.gpgImported)
            coVerify(exactly = 0) { appPreferences.setGpgImported(true) }
        }

    @Test
    fun `prefs failure fails store row and cleans up key blob`() =
        runTest(testDispatcher) {
            stubSuccessfulImport(hasAuthSubkey = false)
            coEvery { appPreferences.setGpgImported(true) } throws RuntimeException("boom")
            every { cryptoOperations.delete() } returns Unit

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            val storeRow = viewModel.state.value.importModal!!.rows.first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }
            assertEquals(GpgImportModalPhase.FAILED, viewModel.state.value.importModal?.phase)
            assertEquals(GpgImportStepStatus.FAILED, storeRow.status)
            assertFalse(viewModel.state.value.gpgImported)
            verify { cryptoOperations.delete() }
        }

    @Test
    fun `choose another key clears modal and input`() {
        viewModel.setGpgKeyText("key")
        viewModel.importGpgKey()

        viewModel.chooseAnotherKey()

        assertEquals("", viewModel.state.value.gpgKeyText)
        assertNull(viewModel.state.value.importModal)
    }

    private fun stubSuccessfulImport(hasAuthSubkey: Boolean) {
        every { cryptoOperations.parseGpgKeyImportCandidate(any()) } returns candidate
        every { cryptoOperations.requireEncryptionSubkey(candidate) } returns Unit
        every { cryptoOperations.requireValidEncryptionSubkey(candidate) } returns Unit
        every { cryptoOperations.requirePrivateEncryptionMaterial(candidate) } returns Unit
        every { cryptoOperations.requirePassphraseProtection(candidate) } returns Unit
        every { cryptoOperations.hasReusableAuthSubkey(candidate) } returns hasAuthSubkey
        every { cryptoOperations.storeImportedGpgKey("key") } returns Unit
    }
}
