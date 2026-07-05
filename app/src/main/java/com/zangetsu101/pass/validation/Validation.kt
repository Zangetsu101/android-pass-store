// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.validation

interface Validation<Candidate, Id> {
    val descriptor: ValidationDescriptor<Id>

    fun validate(candidate: Candidate): ValidationResult
}

data class ValidationDescriptor<Id>(
    val id: Id,
    val label: String,
    val detail: String,
    val required: Boolean,
)

sealed interface ValidationResult {
    data object Passed : ValidationResult

    data object Neutral : ValidationResult

    data class Failed(
        val error: Throwable,
    ) : ValidationResult
}
