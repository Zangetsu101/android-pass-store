// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg.importvalidation

import com.zangetsu101.pass.keymanagement.gpg.GpgImportCandidate
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyInspector
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.onboarding.GpgImportStep
import com.zangetsu101.pass.validation.Validation
import com.zangetsu101.pass.validation.ValidationDescriptor
import com.zangetsu101.pass.validation.ValidationResult
import javax.inject.Inject

class EncryptionSubkeyValidation
    @Inject
    constructor(
        private val inspector: GpgKeyInspector,
    ) : Validation<GpgImportCandidate, GpgImportStep> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportStep.ENCRYPTION_SUBKEY,
                label = "encryption subkey",
                detail = "includes an encryption-capable [E] subkey",
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
    ) : Validation<GpgImportCandidate, GpgImportStep> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportStep.SUBKEY_VALIDITY,
                label = "subkey validity",
                detail = "encryption subkey is not expired or revoked",
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
    ) : Validation<GpgImportCandidate, GpgImportStep> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportStep.PRIVATE_KEY_MATERIAL,
                label = "private key material",
                detail = "encryption subkey includes secret material",
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
    constructor() : Validation<GpgImportCandidate, GpgImportStep> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportStep.PASSPHRASE_PROTECTION,
                label = "passphrase protection",
                detail = "private key material is protected by a passphrase",
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
    ) : Validation<GpgImportCandidate, GpgImportStep> {
        override val descriptor =
            ValidationDescriptor(
                id = GpgImportStep.REUSABLE_GIT_SSH_SUBKEY,
                label = "reusable git ssh subkey",
                detail = "only ed25519 [A] subkeys can be reused for github ssh",
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
