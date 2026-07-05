// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.datastore.preferences.core.Preferences
import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgImportReader
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.EncryptionSubkeyValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PassphraseProtectionValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PrivateKeyMaterialValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.ReusableGitSshSubkeyValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.SubkeyValidityValidation
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.validation.ValidationDescriptor
import com.zangetsu101.pass.validation.ValidationResult
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
    private val gpgKeyStore = mockk<GpgKeyStore>()
    private val importReader = mockk<GpgImportReader>()
    private val encryptionSubkeyValidation = mockk<EncryptionSubkeyValidation>()
    private val subkeyValidityValidation = mockk<SubkeyValidityValidation>()
    private val privateKeyMaterialValidation = mockk<PrivateKeyMaterialValidation>()
    private val passphraseProtectionValidation = mockk<PassphraseProtectionValidation>()
    private val reusableGitSshSubkeyValidation = mockk<ReusableGitSshSubkeyValidation>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val candidate = GpgImportCandidate("key", mockk<PGPSecretKeyRing>())
    private lateinit var viewModel: GpgImportViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        stubValidationDescriptors()
        viewModel =
            GpgImportViewModel(
                gpgKeyStore,
                importReader,
                encryptionSubkeyValidation,
                subkeyValidityValidation,
                privateKeyMaterialValidation,
                passphraseProtectionValidation,
                reusableGitSshSubkeyValidation,
                appPreferences,
                testDispatcher,
            )
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
            assertTrue(
                state.importModal!!
                    .rows
                    .dropLast(2)
                    .all { it.status == GpgImportStepStatus.PASSED },
            )
            assertEquals(
                GpgImportStepStatus.NEUTRAL,
                state.importModal!!
                    .rows
                    .first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }
                    .status,
            )
            assertEquals(
                GpgImportStepStatus.PASSED,
                state.importModal!!
                    .rows
                    .first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }
                    .status,
            )
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
                viewModel.state.value.importModal!!
                    .rows
                    .first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }
                    .status,
            )
        }

    @Test
    fun `importGpgKey throws NoPassphrase fails passphrase row and keeps later rows not checked`() =
        runTest(testDispatcher) {
            every { importReader.parseCandidate(any()) } returns candidate
            every { encryptionSubkeyValidation.validate(candidate) } returns ValidationResult.Passed
            every { subkeyValidityValidation.validate(candidate) } returns ValidationResult.Passed
            every { privateKeyMaterialValidation.validate(candidate) } returns ValidationResult.Passed
            every { passphraseProtectionValidation.validate(candidate) } returns ValidationResult.Failed(KeyImportError.NoPassphrase())

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            val rows =
                viewModel.state.value.importModal!!
                    .rows
            assertEquals(
                GpgImportModalPhase.FAILED,
                viewModel.state.value.importModal
                    ?.phase,
            )
            assertEquals(GpgImportStepStatus.FAILED, rows.first { it.step == GpgImportStep.PASSPHRASE_PROTECTION }.status)
            assertEquals(GpgImportStepStatus.NOT_CHECKED, rows.first { it.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY }.status)
            assertEquals(GpgImportStepStatus.NOT_CHECKED, rows.first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }.status)
            assertFalse(viewModel.state.value.gpgImported)
        }

    @Test
    fun `cancel closes modal and ignores later result`() =
        runTest(testDispatcher) {
            every { importReader.parseCandidate(any()) } returns candidate

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
            every { gpgKeyStore.delete() } returns Unit

            viewModel.setGpgKeyText("key")
            viewModel.importGpgKey()
            advanceUntilIdle()

            val storeRow =
                viewModel.state.value.importModal!!
                    .rows
                    .first { it.step == GpgImportStep.STORE_ENCRYPTED_KEY }
            assertEquals(
                GpgImportModalPhase.FAILED,
                viewModel.state.value.importModal
                    ?.phase,
            )
            assertEquals(GpgImportStepStatus.FAILED, storeRow.status)
            assertFalse(viewModel.state.value.gpgImported)
            verify { gpgKeyStore.delete() }
        }

    @Test
    fun `choose another key clears modal and input`() {
        viewModel.setGpgKeyText("key")
        viewModel.importGpgKey()

        viewModel.chooseAnotherKey()

        assertEquals("", viewModel.state.value.gpgKeyText)
        assertNull(viewModel.state.value.importModal)
    }

    private fun stubValidationDescriptors() {
        every { encryptionSubkeyValidation.descriptor } returns
            ValidationDescriptor(GpgImportStep.ENCRYPTION_SUBKEY, "encryption subkey", "includes an encryption-capable [E] subkey", true)
        every { subkeyValidityValidation.descriptor } returns
            ValidationDescriptor(GpgImportStep.SUBKEY_VALIDITY, "subkey validity", "encryption subkey is not expired or revoked", true)
        every { privateKeyMaterialValidation.descriptor } returns
            ValidationDescriptor(
                GpgImportStep.PRIVATE_KEY_MATERIAL,
                "private key material",
                "encryption subkey includes secret material",
                true,
            )
        every { passphraseProtectionValidation.descriptor } returns
            ValidationDescriptor(
                GpgImportStep.PASSPHRASE_PROTECTION,
                "passphrase protection",
                "private key material is protected by a passphrase",
                true,
            )
        every { reusableGitSshSubkeyValidation.descriptor } returns
            ValidationDescriptor(
                GpgImportStep.REUSABLE_GIT_SSH_SUBKEY,
                "reusable git ssh subkey",
                "only ed25519 [A] subkeys can be reused for github ssh",
                false,
            )
    }

    private fun stubSuccessfulImport(hasAuthSubkey: Boolean) {
        every { importReader.parseCandidate(any()) } returns candidate
        every { encryptionSubkeyValidation.validate(candidate) } returns ValidationResult.Passed
        every { subkeyValidityValidation.validate(candidate) } returns ValidationResult.Passed
        every { privateKeyMaterialValidation.validate(candidate) } returns ValidationResult.Passed
        every { passphraseProtectionValidation.validate(candidate) } returns ValidationResult.Passed
        every { reusableGitSshSubkeyValidation.validate(candidate) } returns
            if (hasAuthSubkey) ValidationResult.Passed else ValidationResult.Neutral
        every { gpgKeyStore.storeImportedGpgKey("key") } returns Unit
    }
}
