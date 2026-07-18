// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg.importvalidation

import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyInspector
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.validation.Validation
import com.zangetsu101.pass.validation.ValidationDescriptor
import com.zangetsu101.pass.validation.ValidationResult
import javax.inject.Inject

enum class GpgImportValidationId {
    ENCRYPTION_SUBKEY,
    SUBKEY_VALIDITY,
    PRIVATE_KEY_MATERIAL,
    PASSPHRASE_PROTECTION,
    REUSABLE_GIT_SSH_SUBKEY,
}

class EncryptionSubkeyValidation
    @Inject
    constructor(
        private val inspector: GpgKeyInspector,
    ) : Validation<GpgImportCandidate, GpgImportValidationId> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportValidationId.ENCRYPTION_SUBKEY,
                required = true,
            )

        override fun validate(candidate: GpgImportCandidate): ValidationResult =
            if (inspector.anyEncryptionFlaggedKeyIds(candidate.secretKeyRing).isEmpty()) {
                ValidationResult.Failed(KeyImportError.NoEncryptionKey())
            } else {
                ValidationResult.Passed
            }
    }

class SubkeyValidityValidation
    @Inject
    constructor(
        private val inspector: GpgKeyInspector,
    ) : Validation<GpgImportCandidate, GpgImportValidationId> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportValidationId.SUBKEY_VALIDITY,
                required = true,
            )

        override fun validate(candidate: GpgImportCandidate): ValidationResult {
            val valid = inspector.validEncryptionKeyIds(candidate.secretKeyRing)
            return if (valid.isEmpty()) {
                ValidationResult.Failed(KeyImportError.ExpiredEncryptionKey(inspector.anyEncryptionFlaggedKeyIds(candidate.secretKeyRing)))
            } else {
                ValidationResult.Passed
            }
        }
    }

class PrivateKeyMaterialValidation
    @Inject
    constructor(
        private val inspector: GpgKeyInspector,
    ) : Validation<GpgImportCandidate, GpgImportValidationId> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportValidationId.PRIVATE_KEY_MATERIAL,
                required = true,
            )

        override fun validate(candidate: GpgImportCandidate): ValidationResult {
            val keys = candidate.secretKeyRing
            val validKeyIds = inspector.validEncryptionKeyIds(keys)
            val privateValidKeyIds = validKeyIds.filter { keyId -> keys.getSecretKey(keyId)?.isPrivateKeyEmpty == false }
            return if (privateValidKeyIds.isEmpty()) {
                ValidationResult.Failed(KeyImportError.PublicKeyOnly(validKeyIds.map { it.shortId() }))
            } else {
                ValidationResult.Passed
            }
        }
    }

class PassphraseProtectionValidation
    @Inject
    constructor() : Validation<GpgImportCandidate, GpgImportValidationId> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportValidationId.PASSPHRASE_PROTECTION,
                required = true,
            )

        override fun validate(candidate: GpgImportCandidate): ValidationResult {
            val unprotected =
                candidate.secretKeyRing
                    .asSequence()
                    .filter { !it.isPrivateKeyEmpty && it.s2KUsage == 0 }
                    .map { it.publicKey.keyID.shortId() }
                    .toList()
            return if (unprotected.isNotEmpty()) {
                ValidationResult.Failed(KeyImportError.NoPassphrase(unprotected))
            } else {
                ValidationResult.Passed
            }
        }
    }

class ReusableGitSshSubkeyValidation
    @Inject
    constructor(
        private val inspector: GpgKeyInspector,
    ) : Validation<GpgImportCandidate, GpgImportValidationId> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportValidationId.REUSABLE_GIT_SSH_SUBKEY,
                required = false,
            )

        override fun validate(candidate: GpgImportCandidate): ValidationResult =
            if (inspector.findAuthSubkey(candidate.secretKeyRing) != null) {
                ValidationResult.Passed
            } else {
                ValidationResult.Neutral
            }
    }

private fun Long.shortId(): String = "%08X".format(this and 0xFFFFFFFFL)
