// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassSecondaryButton
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassTheme
import com.zangetsu101.pass.ui.theme.PassType

@Composable
fun OnboardingGpgImportScreen(
    viewModel: GpgImportViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val bytes =
                    context.contentResolver
                        .openInputStream(it)
                        ?.readBytes()
                        ?: return@let
                viewModel.setGpgKeyFromBytes(bytes)
            }
        }

    OnboardingScaffold(
        step = 1,
        total = 3,
        title = "gpg key",
        subtitle = "provide the keypair used to encrypt/decrypt your store",
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface, PassShapes.small)
                    .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                    .clickable { filePicker.launch(arrayOf("*/*")) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(PassColorsDark.AccentDim, PassShapes.small)
                        .border(1.dp, PassColorsDark.AccentMid, PassShapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = PassColorsDark.Accent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("import from file", style = PassType.Body)
                Text(".asc / .gpg secret key file", style = PassType.Caption)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PassColorsDark.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(PassColorsDark.Border))
            Text("or", style = PassType.Caption, color = PassColorsDark.TextFaint)
            Box(modifier = Modifier.weight(1f).height(1.dp).background(PassColorsDark.Border))
        }
        Spacer(Modifier.height(8.dp))
        Text("paste armored secret key", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        PassTextField(
            value = state.gpgKeyText,
            onValueChange = viewModel::setGpgKeyText,
            placeholder = "-----BEGIN PGP PRIVATE KEY BLOCK-----",
            singleLine = false,
            minLines = 5,
            maxLines = 10,
            isError = state.gpgImportError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "gpg --armor --export-secret-subkeys your@email.com",
            style = PassType.Caption,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "exports only your [E]/[A] subkeys, keeps your master key offline.",
            style = PassType.Caption,
            color = PassColorsDark.TextFaint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your key must be passphrase-protected. You'll enter it when starting a session.",
            style = PassType.Caption,
            color = PassColorsDark.TextFaint,
        )
        Spacer(Modifier.height(16.dp))
        PassPrimaryButton(
            onClick = viewModel::importGpgKey,
            label = "> gpg --import",
            enabled = state.gpgKeyText.isNotBlank() && !state.gpgImported && state.importModal == null,
        )
    }

    state.importModal?.let { modal ->
        GpgImportChecklistDialog(
            modal = modal,
            onCancel = viewModel::cancelImport,
            onClose = viewModel::dismissImportError,
            onChooseAnother = viewModel::chooseAnotherKey,
            onContinue = {
                viewModel.closeImportModal()
                onNext()
            },
        )
    }
}

@Composable
private fun GpgImportChecklistDialog(
    modal: GpgImportModalState,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onChooseAnother: () -> Unit,
    onContinue: () -> Unit,
) {
    val title = modal.title()
    AlertDialog(
        onDismissRequest = {
            when (modal.phase) {
                GpgImportModalPhase.RUNNING -> onCancel()
                GpgImportModalPhase.FAILED -> onClose()
                GpgImportModalPhase.SUCCESS -> Unit
            }
        },
        containerColor = PassColorsDark.Surface,
        titleContentColor = if (modal.phase == GpgImportModalPhase.FAILED) PassColorsDark.Danger else PassColorsDark.TextPrimary,
        textContentColor = PassColorsDark.TextDim,
        title = { Text(title, style = PassType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modal.displayRows().forEach { row ->
                    GpgImportChecklistRowContent(row)
                }
                if (modal.phase == GpgImportModalPhase.SUCCESS) {
                    Text("your gpg key is ready to decrypt your store.", style = PassType.Caption, color = PassColorsDark.TextDim)
                }
            }
        },
        confirmButton = {
            when (modal.phase) {
                GpgImportModalPhase.RUNNING -> PassSecondaryButton(onClick = onCancel, label = "cancel")
                GpgImportModalPhase.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassPrimaryButton(onClick = onChooseAnother, label = "choose another key")
                        PassSecondaryButton(onClick = onClose, label = "close")
                    }
                }
                GpgImportModalPhase.SUCCESS -> PassPrimaryButton(onClick = onContinue, label = "continue")
            }
        },
    )
}

private fun GpgImportModalState.title() =
    when (phase) {
        GpgImportModalPhase.RUNNING -> "checking gpg key"
        GpgImportModalPhase.FAILED -> "import failed"
        GpgImportModalPhase.SUCCESS -> "key imported"
    }

@Composable
private fun GpgImportChecklistDialogPreview(
    modal: GpgImportModalState,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onChooseAnother: () -> Unit,
    onContinue: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(PassColorsDark.Background)
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface, PassShapes.medium)
                    .border(1.dp, PassColorsDark.Border2, PassShapes.medium)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                modal.title(),
                style = PassType.Title,
                color = if (modal.phase == GpgImportModalPhase.FAILED) PassColorsDark.Danger else PassColorsDark.TextPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modal.displayRows().forEach { row -> GpgImportChecklistRowContent(row) }
                if (modal.phase == GpgImportModalPhase.SUCCESS) {
                    Text("your gpg key is ready to decrypt your store.", style = PassType.Caption, color = PassColorsDark.TextDim)
                }
            }
            when (modal.phase) {
                GpgImportModalPhase.RUNNING -> PassSecondaryButton(onClick = onCancel, label = "cancel")
                GpgImportModalPhase.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassPrimaryButton(onClick = onChooseAnother, label = "choose another key")
                        PassSecondaryButton(onClick = onClose, label = "close")
                    }
                }
                GpgImportModalPhase.SUCCESS -> PassPrimaryButton(onClick = onContinue, label = "continue")
            }
        }
    }
}

private data class GpgImportChecklistDisplayRow(
    val label: String,
    val detail: String?,
    val status: GpgImportStepStatus,
)

private fun GpgImportModalState.displayRows(): List<GpgImportChecklistDisplayRow> {
    val rowsByStep = rows.associateBy { it.step }
    fun row(step: GpgImportStep) = requireNotNull(rowsByStep[step])

    val decryptionRows =
        listOf(
            row(GpgImportStep.ENCRYPTION_SUBKEY),
            row(GpgImportStep.SUBKEY_VALIDITY),
            row(GpgImportStep.PRIVATE_KEY_MATERIAL),
        )
    val failedDecryptionRow = decryptionRows.firstOrNull { it.status == GpgImportStepStatus.FAILED }
    val runningDecryptionRow = decryptionRows.firstOrNull { it.status == GpgImportStepStatus.RUNNING }
    val decryptionStatus =
        when {
            failedDecryptionRow != null -> GpgImportStepStatus.FAILED
            runningDecryptionRow != null -> GpgImportStepStatus.RUNNING
            decryptionRows.all { it.status == GpgImportStepStatus.PASSED } -> GpgImportStepStatus.PASSED
            decryptionRows.any { it.status == GpgImportStepStatus.PASSED } -> GpgImportStepStatus.RUNNING
            else -> GpgImportStepStatus.NOT_CHECKED
        }

    val secretKeyRow = row(GpgImportStep.SECRET_KEY_FILE)
    val passphraseRow = row(GpgImportStep.PASSPHRASE_PROTECTION)
    val sshRow = row(GpgImportStep.REUSABLE_GIT_SSH_SUBKEY)
    val storeRow = row(GpgImportStep.STORE_ENCRYPTED_KEY)

    return listOf(
        secretKeyRow.toDisplayRow("secret key recognized"),
        GpgImportChecklistDisplayRow(
            label = "decryption key usable",
            detail = when (decryptionStatus) {
                GpgImportStepStatus.FAILED -> failedDecryptionRow?.error?.message ?: failedDecryptionRow?.detail
                GpgImportStepStatus.RUNNING -> runningDecryptionRow?.detail ?: "checking decryption key…"
                else -> null
            },
            status = decryptionStatus,
        ),
        passphraseRow.toDisplayRow("passphrase protected"),
        sshRow.toDisplayRow(if (sshRow.status == GpgImportStepStatus.NEUTRAL) "github ssh key not reusable" else "github ssh key reusable"),
        storeRow.toDisplayRow("stored securely"),
    )
}

private fun GpgImportChecklistRow.toDisplayRow(label: String): GpgImportChecklistDisplayRow =
    GpgImportChecklistDisplayRow(
        label = label,
        detail = when (status) {
            GpgImportStepStatus.FAILED -> error?.message ?: detail
            GpgImportStepStatus.RUNNING,
            GpgImportStepStatus.NEUTRAL,
            -> detail
            GpgImportStepStatus.NOT_CHECKED,
            GpgImportStepStatus.PASSED,
            -> null
        },
        status = status,
    )

@Composable
private fun GpgImportChecklistRowContent(row: GpgImportChecklistDisplayRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChecklistStatus(status = row.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = PassType.Body, color = row.status.color())
            row.detail?.let { detail ->
                Text(detail, style = PassType.Caption, color = if (row.status == GpgImportStepStatus.FAILED) PassColorsDark.Danger else PassColorsDark.TextFaint)
            }
        }
    }
}

@Composable
private fun ChecklistStatus(status: GpgImportStepStatus) {
    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (status) {
            GpgImportStepStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PassColorsDark.Accent, strokeWidth = 2.dp)
            GpgImportStepStatus.PASSED -> Text("✓", style = PassType.Body, color = PassColorsDark.Accent)
            GpgImportStepStatus.FAILED -> Text("!", style = PassType.Body, color = PassColorsDark.Danger)
            GpgImportStepStatus.NEUTRAL -> Text("i", style = PassType.Caption, color = PassColorsDark.TextFaint)
            GpgImportStepStatus.NOT_CHECKED -> Text("•", style = PassType.Caption, color = PassColorsDark.TextFaint)
        }
    }
}

private fun GpgImportStepStatus.color() =
    when (this) {
        GpgImportStepStatus.FAILED -> PassColorsDark.Danger
        GpgImportStepStatus.NOT_CHECKED,
        GpgImportStepStatus.NEUTRAL,
        -> PassColorsDark.TextDim
        GpgImportStepStatus.RUNNING,
        GpgImportStepStatus.PASSED,
        -> PassColorsDark.TextPrimary
    }

// ---- Previews ----

private const val PREVIEW_BG = 0xFF0B0D0BL

@Preview(name = "gpg import · running", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogRunningPreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            modal = GpgImportModalState(
                phase = GpgImportModalPhase.RUNNING,
                rows = previewRows(GpgImportStep.PASSPHRASE_PROTECTION),
            ),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

@Preview(name = "gpg import · malformed failure", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogMalformedFailurePreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            modal = GpgImportModalState(
                phase = GpgImportModalPhase.FAILED,
                rows = previewRows(
                    failedStep = GpgImportStep.SECRET_KEY_FILE,
                    error = GpgImportError(
                        title = "unrecognized key",
                        message = "this doesn't look like an armored gpg secret key. check you exported a private key, not a public one.",
                    ),
                ),
            ),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

@Preview(name = "gpg import · passphrase failure", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogPassphraseFailurePreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            modal = GpgImportModalState(
                phase = GpgImportModalPhase.FAILED,
                rows = previewRows(
                    failedStep = GpgImportStep.PASSPHRASE_PROTECTION,
                    error = GpgImportError(
                        title = "key not protected",
                        message = "this key is not passphrase-protected (1A2B3C4D). re-export it with a passphrase — at-rest security depends on it.",
                    ),
                ),
            ),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

@Preview(name = "gpg import · success with auth subkey", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogSuccessWithAuthPreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            modal = GpgImportModalState(
                phase = GpgImportModalPhase.SUCCESS,
                rows = previewRows(authSubkeyFound = true, stored = true),
            ),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

@Preview(name = "gpg import · success without auth subkey", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogSuccessWithoutAuthPreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            modal = GpgImportModalState(
                phase = GpgImportModalPhase.SUCCESS,
                rows = previewRows(authSubkeyFound = false, stored = true),
            ),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

private fun previewRows(
    runningStep: GpgImportStep? = null,
    failedStep: GpgImportStep? = null,
    error: GpgImportError? = null,
    authSubkeyFound: Boolean? = null,
    stored: Boolean = false,
): List<GpgImportChecklistRow> =
    previewBaseRows().map { row ->
        val status =
            when {
                row.step == runningStep -> GpgImportStepStatus.RUNNING
                row.step == failedStep -> GpgImportStepStatus.FAILED
                failedStep != null && row.step.ordinal > failedStep.ordinal -> GpgImportStepStatus.NOT_CHECKED
                authSubkeyFound != null && row.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY ->
                    if (authSubkeyFound) GpgImportStepStatus.PASSED else GpgImportStepStatus.NEUTRAL
                stored && row.step == GpgImportStep.STORE_ENCRYPTED_KEY -> GpgImportStepStatus.PASSED
                runningStep != null && row.step.ordinal < runningStep.ordinal -> GpgImportStepStatus.PASSED
                runningStep != null -> GpgImportStepStatus.NOT_CHECKED
                failedStep != null -> GpgImportStepStatus.PASSED
                authSubkeyFound != null && row.step != GpgImportStep.REUSABLE_GIT_SSH_SUBKEY -> GpgImportStepStatus.PASSED
                else -> row.status
            }
        val detail =
            when (authSubkeyFound) {
                true if row.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY -> "you can reuse this key for github ssh on the next step"
                false if row.step == GpgImportStep.REUSABLE_GIT_SSH_SUBKEY -> "only ed25519 [A] subkeys can be reused for github ssh"
                else -> row.detail
            }
        row.copy(status = status, detail = detail, error = if (row.step == failedStep) error else null)
    }

private fun previewBaseRows(): List<GpgImportChecklistRow> =
    listOf(
        GpgImportChecklistRow(GpgImportStep.SECRET_KEY_FILE, "secret key file", "looks like an openpgp secret key ring"),
        GpgImportChecklistRow(GpgImportStep.ENCRYPTION_SUBKEY, "encryption subkey", "includes an encryption-capable [E] subkey"),
        GpgImportChecklistRow(GpgImportStep.SUBKEY_VALIDITY, "subkey validity", "encryption subkey is not expired or revoked"),
        GpgImportChecklistRow(GpgImportStep.PRIVATE_KEY_MATERIAL, "private key material", "encryption subkey includes secret material"),
        GpgImportChecklistRow(GpgImportStep.PASSPHRASE_PROTECTION, "passphrase protection", "private key material is protected by a passphrase"),
        GpgImportChecklistRow(GpgImportStep.REUSABLE_GIT_SSH_SUBKEY, "reusable git ssh subkey", "only ed25519 [A] subkeys can be reused for github ssh"),
        GpgImportChecklistRow(GpgImportStep.STORE_ENCRYPTED_KEY, "store encrypted key", "save the protected key ring in encrypted app storage"),
    )
