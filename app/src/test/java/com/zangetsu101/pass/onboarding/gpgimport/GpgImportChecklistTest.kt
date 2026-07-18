// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding.gpgimport

import com.zangetsu101.pass.keymanagement.gpg.importvalidation.GpgImportValidationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GpgImportChecklistTest {
    @Test
    fun `initial checklist has five unchecked groups without details`() {
        val groups = GpgImportChecklist.initial().groups

        assertEquals(
            listOf(
                ChecklistGroupId.SECRET_KEY_RECOGNIZED,
                ChecklistGroupId.DECRYPTION_KEY_USABLE,
                ChecklistGroupId.PASSPHRASE_PROTECTED,
                ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE,
                ChecklistGroupId.STORED_SECURELY,
            ),
            groups.map { it.id },
        )
        assertEquals(List(5) { StepStatus.NOT_CHECKED }, groups.map { it.status })
        groups.forEach { assertNull(it.detail) }
    }

    @Test
    fun `parsing and storage transitions update independent groups immutably`() {
        val initial = GpgImportChecklist.initial()
        val parsing = initial.withParsingStatus(StepStatus.RUNNING)
        val stored =
            parsing
                .withParsingStatus(StepStatus.PASSED)
                .withStorageStatus(StepStatus.PASSED)

        assertEquals(StepStatus.NOT_CHECKED, initial.group(ChecklistGroupId.SECRET_KEY_RECOGNIZED).status)
        assertEquals(StepStatus.RUNNING, parsing.group(ChecklistGroupId.SECRET_KEY_RECOGNIZED).status)
        assertEquals(StepStatus.PASSED, stored.group(ChecklistGroupId.SECRET_KEY_RECOGNIZED).status)
        assertEquals(StepStatus.PASSED, stored.group(ChecklistGroupId.STORED_SECURELY).status)
    }

    @Test
    fun `decryption group rolls up its three validation steps`() {
        val initial = GpgImportChecklist.initial()
        val running =
            initial.withValidationStatus(
                GpgImportValidationId.ENCRYPTION_SUBKEY,
                StepStatus.RUNNING,
            )
        val partiallyPassed =
            running
                .withValidationStatus(GpgImportValidationId.ENCRYPTION_SUBKEY, StepStatus.PASSED)
                .withValidationStatus(GpgImportValidationId.SUBKEY_VALIDITY, StepStatus.PASSED)
        val passed =
            partiallyPassed.withValidationStatus(
                GpgImportValidationId.PRIVATE_KEY_MATERIAL,
                StepStatus.PASSED,
            )

        assertEquals(StepStatus.NOT_CHECKED, initial.group(ChecklistGroupId.DECRYPTION_KEY_USABLE).status)
        assertEquals(StepStatus.RUNNING, running.group(ChecklistGroupId.DECRYPTION_KEY_USABLE).status)
        assertEquals(
            "includes an encryption-capable [E] subkey",
            running.group(ChecklistGroupId.DECRYPTION_KEY_USABLE).detail,
        )
        assertEquals(StepStatus.RUNNING, partiallyPassed.group(ChecklistGroupId.DECRYPTION_KEY_USABLE).status)
        assertEquals(StepStatus.PASSED, passed.group(ChecklistGroupId.DECRYPTION_KEY_USABLE).status)
    }

    @Test
    fun `failed child takes precedence and supplies group detail`() {
        val error = ImportError("expired", "encryption subkey expired")
        val checklist =
            GpgImportChecklist
                .initial()
                .withValidationStatus(GpgImportValidationId.ENCRYPTION_SUBKEY, StepStatus.RUNNING)
                .withValidationStatus(GpgImportValidationId.SUBKEY_VALIDITY, StepStatus.FAILED, error)

        val group = checklist.group(ChecklistGroupId.DECRYPTION_KEY_USABLE)
        assertEquals(StepStatus.FAILED, group.status)
        assertEquals("encryption subkey expired", group.detail)
    }

    @Test
    fun `neutral reusable ssh validation uses optional outcome copy`() {
        val checklist =
            GpgImportChecklist
                .initial()
                .withValidationStatus(
                    GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY,
                    StepStatus.NEUTRAL,
                )

        val group = checklist.group(ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE)
        assertEquals(StepStatus.NEUTRAL, group.status)
        assertEquals("github ssh key not reusable", group.label)
        assertEquals("only ed25519 [A] subkeys can be reused for github ssh", group.detail)
    }

    private fun GpgImportChecklist.group(id: ChecklistGroupId): ChecklistGroup = groups.first { it.id == id }
}
