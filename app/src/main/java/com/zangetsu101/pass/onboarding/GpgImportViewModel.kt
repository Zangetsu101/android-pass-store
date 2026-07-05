// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.zangetsu101.pass.validation.Validation
import com.zangetsu101.pass.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

private const val CHECK_STEP_MIN_MS = 800L

enum class GpgImportStep {
    SECRET_KEY_FILE,
    ENCRYPTION_SUBKEY,
    SUBKEY_VALIDITY,
    PRIVATE_KEY_MATERIAL,
    PASSPHRASE_PROTECTION,
    REUSABLE_GIT_SSH_SUBKEY,
    STORE_ENCRYPTED_KEY,
}

enum class GpgImportStepStatus { NOT_CHECKED, RUNNING, PASSED, FAILED, NEUTRAL }

data class GpgImportChecklistRow(
    val step: GpgImportStep,
    val label: String,
    val detail: String,
    val status: GpgImportStepStatus = GpgImportStepStatus.NOT_CHECKED,
    val error: GpgImportError? = null,
)

enum class GpgImportModalPhase { RUNNING, FAILED, SUCCESS }

data class GpgImportModalState(
    val phase: GpgImportModalPhase,
    val rows: List<GpgImportChecklistRow>,
)

data class GpgImportError(
    val title: String,
    val message: String,
)

data class GpgImportUiState(
    val gpgKeyText: String = "",
    val gpgImportError: GpgImportError? = null,
    val gpgImported: Boolean = false,
    val importModal: GpgImportModalState? = null,
)

@HiltViewModel
class GpgImportViewModel
    @Inject
    constructor(
        private val gpgKeyStore: GpgKeyStore,
        private val importReader: GpgImportReader,
        private val encryptionSubkeyValidation: EncryptionSubkeyValidation,
        private val subkeyValidityValidation: SubkeyValidityValidation,
        private val privateKeyMaterialValidation: PrivateKeyMaterialValidation,
        private val passphraseProtectionValidation: PassphraseProtectionValidation,
        private val reusableGitSshSubkeyValidation: ReusableGitSshSubkeyValidation,
        private val appPreferences: AppPreferences,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GpgImportUiState())
        val state: StateFlow<GpgImportUiState> = _state.asStateFlow()
        private var importJob: Job? = null

        fun dismissImportError() {
            closeImportModal(keepKeyInput = true)
        }

        fun closeImportModal(keepKeyInput: Boolean = true) {
            importJob?.cancel()
            importJob = null
            _state.update {
                it.copy(
                    gpgKeyText = if (keepKeyInput) it.gpgKeyText else "",
                    gpgImportError = null,
                    importModal = null,
                )
            }
        }

        fun cancelImport() {
            closeImportModal(keepKeyInput = true)
        }

        fun chooseAnotherKey() {
            closeImportModal(keepKeyInput = false)
        }

        fun setGpgKeyText(text: String) {
            importJob?.cancel()
            importJob = null
            _state.update {
                it.copy(
                    gpgKeyText = text,
                    gpgImportError = null,
                    gpgImported = false,
                    importModal = null,
                )
            }
        }

        fun setGpgKeyFromBytes(bytes: ByteArray) {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.trimStart().startsWith("-----BEGIN PGP")) {
                setGpgKeyText(text)
                return
            }
            viewModelScope.launch {
                try {
                    val armored = withContext(ioDispatcher) { importReader.armor(bytes) }
                    setGpgKeyText(armored)
                } catch (e: KeyImportError) {
                    val error = GpgImportError(e.title, e.message ?: "invalid or unrecognized key file")
                    _state.update {
                        it.copy(
                            gpgImportError = error,
                            importModal =
                                GpgImportModalState(
                                    GpgImportModalPhase.FAILED,
                                    initialRows().map { row ->
                                        if (row.step == GpgImportStep.SECRET_KEY_FILE) {
                                            row.copy(status = GpgImportStepStatus.FAILED, error = error)
                                        } else {
                                            row
                                        }
                                    },
                                ),
                        )
                    }
                }
            }
        }

        fun importGpgKey() {
            importJob?.cancel()
            _state.update {
                it.copy(
                    gpgImported = false,
                    gpgImportError = null,
                    importModal = GpgImportModalState(GpgImportModalPhase.RUNNING, initialRows()),
                )
            }
            importJob =
                viewModelScope.launch {
                    try {
                        val candidate = parseCandidate()
                        for (validation in validationGroup()) {
                            runValidation(candidate, validation)
                        }
                        runStoreStep(candidate.armoredKey)
                        if (!isActive) return@launch
                        _state.update {
                            it.copy(
                                gpgImported = true,
                                gpgImportError = null,
                                importModal = it.importModal?.copy(phase = GpgImportModalPhase.SUCCESS),
                            )
                        }
                    } catch (_: CancellationException) {
                        return@launch
                    } catch (e: KeyImportError) {
                        if (!isActive) return@launch
                        failCurrentStep(e)
                    } catch (e: Exception) {
                        if (!isActive || _state.value.importModal?.phase == GpgImportModalPhase.FAILED) return@launch
                        failStep(
                            GpgImportStep.STORE_ENCRYPTED_KEY,
                            GpgImportError("could not save key", "could not save the key. please try again."),
                        )
                    }
                }
        }

        private suspend fun parseCandidate(): GpgImportCandidate {
            lateinit var candidate: GpgImportCandidate
            runRequiredStep(GpgImportStep.SECRET_KEY_FILE) {
                candidate = importReader.parseCandidate(_state.value.gpgKeyText)
            }
            return candidate
        }

        private suspend fun runRequiredStep(
            step: GpgImportStep,
            block: suspend () -> Unit,
        ) {
            markRunning(step)
            try {
                withMinimumStepTime { withContext(ioDispatcher) { block() } }
                markStep(step, GpgImportStepStatus.PASSED)
            } catch (e: KeyImportError) {
                markStep(step, GpgImportStepStatus.FAILED, GpgImportError(e.title, e.message ?: "import failed"))
                throw e
            }
        }

        private suspend fun runValidation(
            candidate: GpgImportCandidate,
            validation: Validation<GpgImportCandidate, GpgImportStep>,
        ) {
            val step = validation.descriptor.id
            markRunning(step)
            val result =
                try {
                    withMinimumStepTime { withContext(ioDispatcher) { validation.validate(candidate) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failStep(step, GpgImportError("validation failed", "could not validate the key. please try again."))
                    throw e
                }
            when (result) {
                ValidationResult.Passed -> markValidationPassed(step)
                ValidationResult.Neutral -> markOptionalNeutral(step)
                is ValidationResult.Failed -> failValidation(step, result.error)
            }
        }

        private suspend fun runStoreStep(armoredKey: String) {
            markRunning(GpgImportStep.STORE_ENCRYPTED_KEY)
            try {
                withMinimumStepTime {
                    withContext(ioDispatcher) { gpgKeyStore.storeImportedGpgKey(armoredKey) }
                    appPreferences.setGpgImported(true)
                }
                markStep(GpgImportStep.STORE_ENCRYPTED_KEY, GpgImportStepStatus.PASSED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(ioDispatcher) { runCatching { gpgKeyStore.delete() } }
                failStep(
                    GpgImportStep.STORE_ENCRYPTED_KEY,
                    GpgImportError("could not save key", "could not save the key. please try again."),
                )
                throw e
            }
        }

        private suspend fun <T> withMinimumStepTime(block: suspend () -> T): T =
            coroutineScope {
                val minimumDelay = async { delay(timeMillis = CHECK_STEP_MIN_MS) }
                try {
                    val result = block()
                    minimumDelay.await()
                    result
                } catch (e: Throwable) {
                    minimumDelay.await()
                    throw e
                }
            }

        private fun validationGroup(): List<Validation<GpgImportCandidate, GpgImportStep>> =
            listOf(
                encryptionSubkeyValidation,
                subkeyValidityValidation,
                privateKeyMaterialValidation,
                passphraseProtectionValidation,
                reusableGitSshSubkeyValidation,
            )

        private fun markRunning(step: GpgImportStep) = markStep(step, GpgImportStepStatus.RUNNING)

        private fun markStep(
            step: GpgImportStep,
            status: GpgImportStepStatus,
            error: GpgImportError? = null,
        ) {
            updateRow(step) { it.copy(status = status, error = error) }
        }

        private fun failCurrentStep(error: KeyImportError) {
            if (_state.value.importModal?.phase == GpgImportModalPhase.FAILED) return
            _state.update { state ->
                state.copy(
                    gpgImportError = GpgImportError(error.title, error.message ?: "import failed"),
                    gpgImported = false,
                    importModal = state.importModal?.copy(phase = GpgImportModalPhase.FAILED),
                )
            }
        }

        private fun markValidationPassed(step: GpgImportStep) {
            if (step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY) {
                updateRow(step) {
                    it.copy(
                        status = GpgImportStepStatus.PASSED,
                        detail = "you can reuse this key for github ssh on the next step",
                        error = null,
                    )
                }
            } else {
                markStep(step, GpgImportStepStatus.PASSED)
            }
        }

        private fun markOptionalNeutral(step: GpgImportStep) {
            updateRow(step) { it.copy(status = GpgImportStepStatus.NEUTRAL, error = null) }
        }

        private fun failValidation(
            step: GpgImportStep,
            error: Throwable,
        ): Nothing {
            val importError =
                if (error is KeyImportError) {
                    GpgImportError(error.title, error.message ?: "import failed")
                } else {
                    GpgImportError("validation failed", "could not validate the key. please try again.")
                }
            failStep(step, importError)
            throw if (error is Exception) error else RuntimeException(error)
        }

        private fun failStep(
            step: GpgImportStep,
            error: GpgImportError,
        ) {
            updateRow(step) { it.copy(status = GpgImportStepStatus.FAILED, error = error) }
            _state.update { state ->
                state.copy(
                    gpgImportError = error,
                    gpgImported = false,
                    importModal = state.importModal?.copy(phase = GpgImportModalPhase.FAILED),
                )
            }
        }

        private fun updateRow(
            step: GpgImportStep,
            transform: (GpgImportChecklistRow) -> GpgImportChecklistRow,
        ) {
            _state.update { state ->
                state.copy(
                    importModal =
                        state.importModal?.copy(
                            rows = state.importModal.rows.map { if (it.step == step) transform(it) else it },
                        ),
                )
            }
        }

        private fun initialRows(): List<GpgImportChecklistRow> =
            listOf(GpgImportChecklistRow(GpgImportStep.SECRET_KEY_FILE, "secret key file", "looks like an openpgp secret key ring")) +
                validationGroup().map { validation ->
                    val descriptor = validation.descriptor
                    GpgImportChecklistRow(descriptor.id, descriptor.label, descriptor.detail)
                } +
                GpgImportChecklistRow(
                    GpgImportStep.STORE_ENCRYPTED_KEY,
                    "store encrypted key",
                    "save the protected key ring in encrypted app storage",
                )
    }
