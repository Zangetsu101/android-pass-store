// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
        private val cryptoOperations: GpgKeyStore,
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
                    val armored = withContext(ioDispatcher) { cryptoOperations.armorGpgKey(bytes) }
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
                        runRequiredStep(GpgImportStep.ENCRYPTION_SUBKEY) { cryptoOperations.requireEncryptionSubkey(candidate) }
                        runRequiredStep(GpgImportStep.SUBKEY_VALIDITY) { cryptoOperations.requireValidEncryptionSubkey(candidate) }
                        runRequiredStep(GpgImportStep.PRIVATE_KEY_MATERIAL) { cryptoOperations.requirePrivateEncryptionMaterial(candidate) }
                        runRequiredStep(GpgImportStep.PASSPHRASE_PROTECTION) { cryptoOperations.requirePassphraseProtection(candidate) }
                        runAuthSubkeyStep(candidate)
                        runStoreStep(candidate.armoredKey)
                        if (!isActive) return@launch
                        _state.update { it.copy(gpgImported = true, gpgImportError = null, importModal = it.importModal?.copy(phase = GpgImportModalPhase.SUCCESS)) }
                    } catch (_: CancellationException) {
                        return@launch
                    } catch (e: KeyImportError) {
                        if (!isActive) return@launch
                        failCurrentStep(e)
                    } catch (e: Exception) {
                        if (!isActive) return@launch
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
                candidate = cryptoOperations.parseGpgKeyImportCandidate(_state.value.gpgKeyText)
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

        private suspend fun runAuthSubkeyStep(candidate: GpgImportCandidate) {
            markRunning(GpgImportStep.REUSABLE_GIT_SSH_SUBKEY)
            val found = withMinimumStepTime { withContext(ioDispatcher) { cryptoOperations.hasReusableAuthSubkey(candidate) } }
            val detail =
                if (found) {
                    "you can reuse this key for github ssh on the next step"
                } else {
                    "only ed25519 [A] subkeys can be reused for github ssh"
                }
            updateRow(GpgImportStep.REUSABLE_GIT_SSH_SUBKEY) {
                it.copy(status = if (found) GpgImportStepStatus.PASSED else GpgImportStepStatus.NEUTRAL, detail = detail, error = null)
            }
        }

        private suspend fun runStoreStep(armoredKey: String) {
            markRunning(GpgImportStep.STORE_ENCRYPTED_KEY)
            try {
                withMinimumStepTime {
                    withContext(ioDispatcher) { cryptoOperations.storeImportedGpgKey(armoredKey) }
                    appPreferences.setGpgImported(true)
                }
                markStep(GpgImportStep.STORE_ENCRYPTED_KEY, GpgImportStepStatus.PASSED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(ioDispatcher) { runCatching { cryptoOperations.delete() } }
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

        private fun failStep(
            step: GpgImportStep,
            error: GpgImportError,
        ) {
            updateRow(step) { it.copy(status = GpgImportStepStatus.FAILED, error = error) }
            _state.update { state ->
                state.copy(gpgImportError = error, gpgImported = false, importModal = state.importModal?.copy(phase = GpgImportModalPhase.FAILED))
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
            listOf(
                GpgImportChecklistRow(GpgImportStep.SECRET_KEY_FILE, "secret key file", "looks like an openpgp secret key ring"),
                GpgImportChecklistRow(GpgImportStep.ENCRYPTION_SUBKEY, "encryption subkey", "includes an encryption-capable [E] subkey"),
                GpgImportChecklistRow(GpgImportStep.SUBKEY_VALIDITY, "subkey validity", "encryption subkey is not expired or revoked"),
                GpgImportChecklistRow(GpgImportStep.PRIVATE_KEY_MATERIAL, "private key material", "encryption subkey includes secret material"),
                GpgImportChecklistRow(GpgImportStep.PASSPHRASE_PROTECTION, "passphrase protection", "private key material is protected by a passphrase"),
                GpgImportChecklistRow(GpgImportStep.REUSABLE_GIT_SSH_SUBKEY, "reusable git ssh subkey", "only ed25519 [A] subkeys can be reused for github ssh"),
                GpgImportChecklistRow(GpgImportStep.STORE_ENCRYPTED_KEY, "store encrypted key", "save the protected key ring in encrypted app storage"),
            )
    }
