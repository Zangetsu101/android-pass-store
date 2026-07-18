// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding.gpgimport

enum class StepStatus { NOT_CHECKED, RUNNING, PASSED, FAILED, NEUTRAL }

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

enum class ModalPhase { RUNNING, FAILED, SUCCESS }

class ImportModalState internal constructor(
    val phase: ModalPhase,
    private val checklist: GpgImportChecklist,
) {
    val groups: List<ChecklistGroup>
        get() = checklist.groups

    internal fun updateChecklist(transform: (GpgImportChecklist) -> GpgImportChecklist): ImportModalState =
        ImportModalState(phase, transform(checklist))

    internal fun withPhase(phase: ModalPhase): ImportModalState = ImportModalState(phase, checklist)
}

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
