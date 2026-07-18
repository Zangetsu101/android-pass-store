// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding.gpgimport

import com.zangetsu101.pass.keymanagement.gpg.importvalidation.GpgImportValidationId

internal class GpgImportChecklist private constructor(
    private val stepStates: List<ChecklistStepState>,
) {
    val groups: List<ChecklistGroup>
        get() {
            val stepsById = stepStates.associateBy(ChecklistStepState::step)
            return groupSpecs.map { it.toGroup(stepsById) }
        }

    fun withParsingStatus(
        status: StepStatus,
        error: ImportError? = null,
    ): GpgImportChecklist = withStatus(ImportStep.SECRET_KEY_FILE, status, error)

    fun withValidationStatus(
        id: GpgImportValidationId,
        status: StepStatus,
        error: ImportError? = null,
    ): GpgImportChecklist = withStatus(id.toImportStep(), status, error)

    fun withStorageStatus(
        status: StepStatus,
        error: ImportError? = null,
    ): GpgImportChecklist = withStatus(ImportStep.STORE_ENCRYPTED_KEY, status, error)

    private fun withStatus(
        step: ImportStep,
        status: StepStatus,
        error: ImportError?,
    ): GpgImportChecklist =
        GpgImportChecklist(
            stepStates.map { if (it.step == step) it.copy(status = status, error = error) else it },
        )

    companion object {
        fun initial(): GpgImportChecklist =
            GpgImportChecklist(stepSpecs.map { ChecklistStepState(it.step, it.runningDetail) })
    }
}

private enum class ImportStep {
    SECRET_KEY_FILE,
    VALIDATION_ENCRYPTION_SUBKEY,
    VALIDATION_SUBKEY_VALIDITY,
    VALIDATION_PRIVATE_KEY_MATERIAL,
    VALIDATION_PASSPHRASE_PROTECTION,
    VALIDATION_REUSABLE_GIT_SSH_SUBKEY,
    STORE_ENCRYPTED_KEY,
}

private data class ChecklistStepState(
    val step: ImportStep,
    val runningDetail: String,
    val status: StepStatus = StepStatus.NOT_CHECKED,
    val error: ImportError? = null,
)

private data class ChecklistStepSpec(
    val step: ImportStep,
    val runningDetail: String,
)

private data class ChecklistGroupSpec(
    val id: ChecklistGroupId,
    val label: String,
    val steps: List<ImportStep>,
    val neutralLabel: String? = null,
    val neutralDetail: String? = null,
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
