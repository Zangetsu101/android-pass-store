// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgImportReader
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.GpgImportValidationId
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.validation.Validation
import com.zangetsu101.pass.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

private enum class ImportStep {
    SECRET_KEY_FILE,
    VALIDATION_ENCRYPTION_SUBKEY,
    VALIDATION_SUBKEY_VALIDITY,
    VALIDATION_PRIVATE_KEY_MATERIAL,
    VALIDATION_PASSPHRASE_PROTECTION,
    VALIDATION_REUSABLE_GIT_SSH_SUBKEY,
    STORE_ENCRYPTED_KEY,
}

enum class StepStatus { NOT_CHECKED, RUNNING, PASSED, FAILED, NEUTRAL }

private data class ChecklistStepState(
    val step: ImportStep,
    val runningDetail: String,
    val status: StepStatus = StepStatus.NOT_CHECKED,
    val error: ImportError? = null,
)

enum class ChecklistGroupId {
    SECRET_KEY_RECOGNIZED,
    DECRYPTION_KEY_USABLE,
    PASSPHRASE_PROTECTED,
    GITHUB_SSH_KEY_REUSABLE,
    STORED_SECURELY,
}

data class ChecklistGroup(
    val id: ChecklistGroupId,
    val label: String,
    val detail: String?,
    val status: StepStatus,
)

private data class ChecklistGroupSpec(
    val id: ChecklistGroupId,
    val label: String,
    val steps: List<ImportStep>,
    val neutralLabel: String? = null,
    val neutralDetail: String? = null,
)

enum class ModalPhase { RUNNING, FAILED, SUCCESS }

data class ImportModalState(
    val phase: ModalPhase,
    val groups: List<ChecklistGroup>,
)

data class ImportError(
    val title: String,
    val message: String,
)

data class GpgImportUiState(
    val gpgKeyText: String = "",
    val gpgImportError: ImportError? = null,
    val gpgImported: Boolean = false,
    val importModal: ImportModalState? = null,
)

@HiltViewModel
class GpgImportViewModel
    @Inject
    constructor(
        private val gpgKeyStore: GpgKeyStore,
        private val importReader: GpgImportReader,
        validations: Set<@JvmSuppressWildcards Validation<GpgImportCandidate, GpgImportValidationId>>,
        private val appPreferences: AppPreferences,
        @Named("IoDispatcher") private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val validationsById = validations.associateBy { it.descriptor.id }
        private var stepStates = initialStepStates()
        private val _state = MutableStateFlow(GpgImportUiState())
        val state: StateFlow<GpgImportUiState> = _state.asStateFlow()
        private var importJob: Job? = null

        init {
            require(validationsById.size == validations.size) { "duplicate gpg import validation ids" }
            require(validationOrder.all { it in validationsById }) { "missing gpg import validations" }
        }

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
                    val error = ImportError(e.title, e.message ?: "invalid or unrecognized key file")
                    stepStates = initialStepStates().mark(ImportStep.SECRET_KEY_FILE, StepStatus.FAILED, error)
                    _state.update {
                        it.copy(
                            gpgImportError = error,
                            importModal = modal(ModalPhase.FAILED),
                        )
                    }
                }
            }
        }

        fun importGpgKey() {
            importJob?.cancel()
            stepStates = initialStepStates()
            _state.update {
                it.copy(
                    gpgImported = false,
                    gpgImportError = null,
                    importModal = modal(ModalPhase.RUNNING),
                )
            }
            importJob =
                viewModelScope.launch {
                    try {
                        val candidate = parseCandidate()
                        for (validation in orderedValidations()) {
                            runValidation(candidate, validation)
                        }
                        runStoreStep(candidate.armoredKey)
                        if (!isActive) return@launch
                        _state.update {
                            it.copy(
                                gpgImported = true,
                                gpgImportError = null,
                                importModal = modal(ModalPhase.SUCCESS),
                            )
                        }
                    } catch (_: CancellationException) {
                        return@launch
                    } catch (e: KeyImportError) {
                        if (!isActive) return@launch
                        failCurrentStep(e)
                    } catch (e: Exception) {
                        if (!isActive || _state.value.importModal?.phase == ModalPhase.FAILED) return@launch
                        failStep(
                            ImportStep.STORE_ENCRYPTED_KEY,
                            ImportError("could not save key", "could not save the key. please try again."),
                        )
                    }
                }
        }

        private suspend fun parseCandidate(): GpgImportCandidate {
            lateinit var candidate: GpgImportCandidate
            runRequiredStep(ImportStep.SECRET_KEY_FILE) {
                candidate = importReader.parseCandidate(_state.value.gpgKeyText)
            }
            return candidate
        }

        private suspend fun runRequiredStep(
            step: ImportStep,
            block: suspend () -> Unit,
        ) {
            markRunning(step)
            try {
                withMinimumStepTime { withContext(ioDispatcher) { block() } }
                markStep(step, StepStatus.PASSED)
            } catch (e: KeyImportError) {
                markStep(step, StepStatus.FAILED, ImportError(e.title, e.message ?: "import failed"))
                throw e
            }
        }

        private suspend fun runValidation(
            candidate: GpgImportCandidate,
            validation: Validation<GpgImportCandidate, GpgImportValidationId>,
        ) {
            val step = validation.descriptor.id.toImportStep()
            markRunning(step)
            val result =
                try {
                    withMinimumStepTime { withContext(ioDispatcher) { validation.validate(candidate) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failStep(step, ImportError("validation failed", "could not validate the key. please try again."))
                    throw e
                }
            when (result) {
                ValidationResult.Passed -> markStep(step, StepStatus.PASSED)
                ValidationResult.Neutral -> markStep(step, StepStatus.NEUTRAL)
                is ValidationResult.Failed -> failValidation(step, result.error)
            }
        }

        private suspend fun runStoreStep(armoredKey: String) {
            markRunning(ImportStep.STORE_ENCRYPTED_KEY)
            try {
                withMinimumStepTime {
                    withContext(ioDispatcher) { gpgKeyStore.storeImportedGpgKey(armoredKey) }
                    appPreferences.setGpgImported(true)
                }
                markStep(ImportStep.STORE_ENCRYPTED_KEY, StepStatus.PASSED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(ioDispatcher) { runCatching { gpgKeyStore.delete() } }
                failStep(
                    ImportStep.STORE_ENCRYPTED_KEY,
                    ImportError("could not save key", "could not save the key. please try again."),
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

        private fun orderedValidations(): List<Validation<GpgImportCandidate, GpgImportValidationId>> =
            validationOrder.map { requireNotNull(validationsById[it]) }

        private fun markRunning(step: ImportStep) = markStep(step, StepStatus.RUNNING)

        private fun markStep(
            step: ImportStep,
            status: StepStatus,
            error: ImportError? = null,
        ) {
            stepStates = stepStates.mark(step, status, error)
            _state.update { state ->
                state.copy(importModal = state.importModal?.copy(groups = groups()))
            }
        }

        private fun failCurrentStep(error: KeyImportError) {
            if (_state.value.importModal?.phase == ModalPhase.FAILED) return
            _state.update { state ->
                state.copy(
                    gpgImportError = ImportError(error.title, error.message ?: "import failed"),
                    gpgImported = false,
                    importModal = state.importModal?.copy(phase = ModalPhase.FAILED, groups = groups()),
                )
            }
        }

        private fun failValidation(
            step: ImportStep,
            error: Throwable,
        ): Nothing {
            val importError =
                if (error is KeyImportError) {
                    ImportError(error.title, error.message ?: "import failed")
                } else {
                    ImportError("validation failed", "could not validate the key. please try again.")
                }
            failStep(step, importError)
            throw if (error is Exception) error else RuntimeException(error)
        }

        private fun failStep(
            step: ImportStep,
            error: ImportError,
        ) {
            stepStates = stepStates.mark(step, StepStatus.FAILED, error)
            _state.update { state ->
                state.copy(
                    gpgImportError = error,
                    gpgImported = false,
                    importModal = state.importModal?.copy(phase = ModalPhase.FAILED, groups = groups()),
                )
            }
        }

        private fun modal(phase: ModalPhase): ImportModalState = ImportModalState(phase, groups())

        private fun groups(): List<ChecklistGroup> = groupSpecs.map { it.toGroup(stepStates.associateBy { stepState -> stepState.step }) }

        private fun initialStepStates(): List<ChecklistStepState> = stepSpecs.map { ChecklistStepState(it.step, it.runningDetail) }
    }

private data class ChecklistStepSpec(
    val step: ImportStep,
    val runningDetail: String,
)

private val validationOrder =
    listOf(
        GpgImportValidationId.ENCRYPTION_SUBKEY,
        GpgImportValidationId.SUBKEY_VALIDITY,
        GpgImportValidationId.PRIVATE_KEY_MATERIAL,
        GpgImportValidationId.PASSPHRASE_PROTECTION,
        GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY,
    )

private val stepSpecs =
    listOf(
        ChecklistStepSpec(ImportStep.SECRET_KEY_FILE, "looks like an openpgp secret key ring"),
        ChecklistStepSpec(ImportStep.VALIDATION_ENCRYPTION_SUBKEY, "includes an encryption-capable [E] subkey"),
        ChecklistStepSpec(ImportStep.VALIDATION_SUBKEY_VALIDITY, "encryption subkey is not expired or revoked"),
        ChecklistStepSpec(ImportStep.VALIDATION_PRIVATE_KEY_MATERIAL, "encryption subkey includes secret material"),
        ChecklistStepSpec(ImportStep.VALIDATION_PASSPHRASE_PROTECTION, "private key material is protected by a passphrase"),
        ChecklistStepSpec(ImportStep.VALIDATION_REUSABLE_GIT_SSH_SUBKEY, "only ed25519 [A] subkeys can be reused for github ssh"),
        ChecklistStepSpec(ImportStep.STORE_ENCRYPTED_KEY, "save the protected key ring in encrypted app storage"),
    )

private val groupSpecs =
    listOf(
        ChecklistGroupSpec(ChecklistGroupId.SECRET_KEY_RECOGNIZED, "secret key recognized", listOf(ImportStep.SECRET_KEY_FILE)),
        ChecklistGroupSpec(
            ChecklistGroupId.DECRYPTION_KEY_USABLE,
            "decryption key usable",
            listOf(
                ImportStep.VALIDATION_ENCRYPTION_SUBKEY,
                ImportStep.VALIDATION_SUBKEY_VALIDITY,
                ImportStep.VALIDATION_PRIVATE_KEY_MATERIAL,
            ),
        ),
        ChecklistGroupSpec(
            ChecklistGroupId.PASSPHRASE_PROTECTED,
            "passphrase protected",
            listOf(ImportStep.VALIDATION_PASSPHRASE_PROTECTION),
        ),
        ChecklistGroupSpec(
            ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE,
            "github ssh key reusable",
            listOf(ImportStep.VALIDATION_REUSABLE_GIT_SSH_SUBKEY),
            neutralLabel = "github ssh key not reusable",
        ),
        ChecklistGroupSpec(ChecklistGroupId.STORED_SECURELY, "stored securely", listOf(ImportStep.STORE_ENCRYPTED_KEY)),
    )

private fun GpgImportValidationId.toImportStep(): ImportStep =
    when (this) {
        GpgImportValidationId.ENCRYPTION_SUBKEY -> ImportStep.VALIDATION_ENCRYPTION_SUBKEY
        GpgImportValidationId.SUBKEY_VALIDITY -> ImportStep.VALIDATION_SUBKEY_VALIDITY
        GpgImportValidationId.PRIVATE_KEY_MATERIAL -> ImportStep.VALIDATION_PRIVATE_KEY_MATERIAL
        GpgImportValidationId.PASSPHRASE_PROTECTION -> ImportStep.VALIDATION_PASSPHRASE_PROTECTION
        GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY -> ImportStep.VALIDATION_REUSABLE_GIT_SSH_SUBKEY
    }

private fun List<ChecklistStepState>.mark(
    step: ImportStep,
    status: StepStatus,
    error: ImportError? = null,
): List<ChecklistStepState> = map { if (it.step == step) it.copy(status = status, error = error) else it }

private fun ChecklistGroupSpec.toGroup(stepsById: Map<ImportStep, ChecklistStepState>): ChecklistGroup {
    val childSteps = steps.map { requireNotNull(stepsById[it]) }
    val status = childSteps.rollupStatus()
    return ChecklistGroup(
        id = id,
        label = if (status == StepStatus.NEUTRAL && neutralLabel != null) neutralLabel else label,
        detail = childSteps.detailFor(status, neutralDetail),
        status = status,
    )
}

private fun List<ChecklistStepState>.rollupStatus(): StepStatus =
    when {
        any { it.status == StepStatus.FAILED } -> StepStatus.FAILED
        any { it.status == StepStatus.RUNNING } -> StepStatus.RUNNING
        all { it.status == StepStatus.PASSED } -> StepStatus.PASSED
        all { it.status == StepStatus.NEUTRAL } -> StepStatus.NEUTRAL
        any { it.status == StepStatus.PASSED || it.status == StepStatus.NEUTRAL } -> StepStatus.RUNNING
        else -> StepStatus.NOT_CHECKED
    }

private fun List<ChecklistStepState>.detailFor(
    status: StepStatus,
    neutralDetail: String?,
): String? =
    when (status) {
        StepStatus.FAILED ->
            firstOrNull { it.status == StepStatus.FAILED }?.error?.message
                ?: firstOrNull { it.status == StepStatus.FAILED }?.runningDetail
        StepStatus.RUNNING -> firstOrNull { it.status == StepStatus.RUNNING }?.runningDetail
        StepStatus.NEUTRAL -> neutralDetail ?: firstOrNull { it.status == StepStatus.NEUTRAL }?.runningDetail
        StepStatus.NOT_CHECKED,
        StepStatus.PASSED,
        -> null
    }
