// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.datastore.preferences.core.Preferences
import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgImportReader
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.EncryptionSubkeyValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.GpgImportValidationId
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PassphraseProtectionValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PrivateKeyMaterialValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.ReusableGitSshSubkeyValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.SubkeyValidityValidation
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.validation.Validation
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
                setOf<Validation<GpgImportCandidate, GpgImportValidationId>>(
                    encryptionSubkeyValidation,
                    subkeyValidityValidation,
                    privateKeyMaterialValidation,
                    passphraseProtectionValidation,
                    reusableGitSshSubkeyValidation,
                ),
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
            val modal = state.importModal!!
            assertEquals(ModalPhase.SUCCESS, modal.phase)
            assertTrue(
                modal.groups
                    .filterNot { it.id in setOf(ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE, ChecklistGroupId.STORED_SECURELY) }
                    .all { it.status == StepStatus.PASSED },
            )
            assertEquals(StepStatus.NEUTRAL, modal.group(ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE).status)
            assertEquals(StepStatus.PASSED, modal.group(ChecklistGroupId.STORED_SECURELY).status)
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
                StepStatus.PASSED,
                viewModel.state.value.importModal!!
                    .group(ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE)
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

            val modal = viewModel.state.value.importModal!!
            assertEquals(
                ModalPhase.FAILED,
                viewModel.state.value.importModal
                    ?.phase,
            )
            assertEquals(StepStatus.FAILED, modal.group(ChecklistGroupId.PASSPHRASE_PROTECTED).status)
            assertEquals(StepStatus.NOT_CHECKED, modal.group(ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE).status)
            assertEquals(StepStatus.NOT_CHECKED, modal.group(ChecklistGroupId.STORED_SECURELY).status)
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

            val storeRow = viewModel.state.value.importModal!!.group(ChecklistGroupId.STORED_SECURELY)
            assertEquals(
                ModalPhase.FAILED,
                viewModel.state.value.importModal
                    ?.phase,
            )
            assertEquals(StepStatus.FAILED, storeRow.status)
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

    private fun ImportModalState.group(id: ChecklistGroupId): ChecklistGroup = groups.first { it.id == id }

    private fun stubValidationDescriptors() {
        every { encryptionSubkeyValidation.descriptor } returns
            ValidationDescriptor(GpgImportValidationId.ENCRYPTION_SUBKEY, true)
        every { subkeyValidityValidation.descriptor } returns
            ValidationDescriptor(GpgImportValidationId.SUBKEY_VALIDITY, true)
        every { privateKeyMaterialValidation.descriptor } returns
            ValidationDescriptor(GpgImportValidationId.PRIVATE_KEY_MATERIAL, true)
        every { passphraseProtectionValidation.descriptor } returns
            ValidationDescriptor(GpgImportValidationId.PASSPHRASE_PROTECTION, true)
        every { reusableGitSshSubkeyValidation.descriptor } returns
            ValidationDescriptor(GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY, false)
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
