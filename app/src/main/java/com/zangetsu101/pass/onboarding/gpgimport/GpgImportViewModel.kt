// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding.gpgimport

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

private sealed interface ImportStage {
    data object Parsing : ImportStage

    data class Validation(
        val id: GpgImportValidationId,
    ) : ImportStage

    data object Storage : ImportStage
}

private class ImportFailure(
    val stage: ImportStage,
    val error: ImportError,
    cause: Throwable,
) : Exception(error.message, cause)

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
                    val error = e.toImportError("invalid or unrecognized key file")
                    val checklist = GpgImportChecklist.initial().withParsingStatus(StepStatus.FAILED, error)
                    _state.update {
                        it.copy(
                            gpgImportError = error,
                            importModal = ImportModalState(ModalPhase.FAILED, checklist),
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
                    importModal = ImportModalState(ModalPhase.RUNNING, GpgImportChecklist.initial()),
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
                                importModal = it.importModal?.withPhase(ModalPhase.SUCCESS),
                            )
                        }
                    } catch (_: CancellationException) {
                        return@launch
                    } catch (failure: ImportFailure) {
                        if (!isActive) return@launch
                        showFailure(failure.stage, failure.error)
                    } catch (_: Exception) {
                        if (!isActive || _state.value.importModal?.phase == ModalPhase.FAILED) return@launch
                        showFailure(
                            ImportStage.Storage,
                            ImportError("could not save key", "could not save the key. please try again."),
                        )
                    }
                }
        }

        private suspend fun parseCandidate(): GpgImportCandidate {
            updateChecklist(ImportStage.Parsing, StepStatus.RUNNING)
            return try {
                val candidate =
                    withMinimumStepTime {
                        withContext(ioDispatcher) { importReader.parseCandidate(_state.value.gpgKeyText) }
                    }
                updateChecklist(ImportStage.Parsing, StepStatus.PASSED)
                candidate
            } catch (e: KeyImportError) {
                throw ImportFailure(ImportStage.Parsing, e.toImportError("import failed"), e)
            }
        }

        private suspend fun runValidation(
            candidate: GpgImportCandidate,
            validation: Validation<GpgImportCandidate, GpgImportValidationId>,
        ) {
            val stage = ImportStage.Validation(validation.descriptor.id)
            updateChecklist(stage, StepStatus.RUNNING)
            val result =
                try {
                    withMinimumStepTime { withContext(ioDispatcher) { validation.validate(candidate) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ImportFailure(
                        stage,
                        ImportError("validation failed", "could not validate the key. please try again."),
                        e,
                    )
                }
            when (result) {
                ValidationResult.Passed -> {
                    updateChecklist(stage, StepStatus.PASSED)
                }

                ValidationResult.Neutral -> {
                    updateChecklist(stage, StepStatus.NEUTRAL)
                }

                is ValidationResult.Failed -> {
                    val error =
                        if (result.error is KeyImportError) {
                            result.error.toImportError("import failed")
                        } else {
                            ImportError("validation failed", "could not validate the key. please try again.")
                        }
                    throw ImportFailure(stage, error, result.error)
                }
            }
        }

        private suspend fun runStoreStep(armoredKey: String) {
            updateChecklist(ImportStage.Storage, StepStatus.RUNNING)
            try {
                withMinimumStepTime {
                    withContext(ioDispatcher) { gpgKeyStore.storeImportedGpgKey(armoredKey) }
                    appPreferences.setGpgImported(true)
                }
                updateChecklist(ImportStage.Storage, StepStatus.PASSED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(ioDispatcher) { runCatching { gpgKeyStore.delete() } }
                throw ImportFailure(
                    ImportStage.Storage,
                    ImportError("could not save key", "could not save the key. please try again."),
                    e,
                )
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

        private fun updateChecklist(
            stage: ImportStage,
            status: StepStatus,
            error: ImportError? = null,
        ) {
            _state.update { state ->
                state.copy(
                    importModal = state.importModal?.updateChecklist { it.withStatus(stage, status, error) },
                )
            }
        }

        private fun showFailure(
            stage: ImportStage,
            error: ImportError,
        ) {
            _state.update { state ->
                state.copy(
                    gpgImportError = error,
                    gpgImported = false,
                    importModal =
                        state.importModal
                            ?.updateChecklist { it.withStatus(stage, StepStatus.FAILED, error) }
                            ?.withPhase(ModalPhase.FAILED),
                )
            }
        }
    }

private fun GpgImportChecklist.withStatus(
    stage: ImportStage,
    status: StepStatus,
    error: ImportError?,
): GpgImportChecklist =
    when (stage) {
        ImportStage.Parsing -> withParsingStatus(status, error)
        is ImportStage.Validation -> withValidationStatus(stage.id, status, error)
        ImportStage.Storage -> withStorageStatus(status, error)
    }

private val validationOrder =
    listOf(
        GpgImportValidationId.ENCRYPTION_SUBKEY,
        GpgImportValidationId.SUBKEY_VALIDITY,
        GpgImportValidationId.PRIVATE_KEY_MATERIAL,
        GpgImportValidationId.PASSPHRASE_PROTECTION,
        GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY,
    )

private fun KeyImportError.toImportError(fallbackMessage: String): ImportError = ImportError(title, message ?: fallbackMessage)
