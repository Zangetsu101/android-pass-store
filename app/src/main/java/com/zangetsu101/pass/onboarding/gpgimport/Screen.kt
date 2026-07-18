// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding.gpgimport

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
import com.zangetsu101.pass.onboarding.OnboardingScaffold
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
    modal: ImportModalState,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onChooseAnother: () -> Unit,
    onContinue: () -> Unit,
) {
    val title = modal.phase.title()
    AlertDialog(
        onDismissRequest = {
            when (modal.phase) {
                ModalPhase.RUNNING -> onCancel()
                ModalPhase.FAILED -> onClose()
                ModalPhase.SUCCESS -> Unit
            }
        },
        containerColor = PassColorsDark.Surface,
        titleContentColor = if (modal.phase == ModalPhase.FAILED) PassColorsDark.Danger else PassColorsDark.TextPrimary,
        textContentColor = PassColorsDark.TextDim,
        title = { Text(title, style = PassType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modal.groups.forEach { row ->
                    GpgImportChecklistRowContent(row)
                }
                if (modal.phase == ModalPhase.SUCCESS) {
                    Text("your gpg key is ready to decrypt your store.", style = PassType.Caption, color = PassColorsDark.TextDim)
                }
            }
        },
        confirmButton = {
            when (modal.phase) {
                ModalPhase.RUNNING -> {
                    PassSecondaryButton(onClick = onCancel, label = "cancel")
                }

                ModalPhase.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassPrimaryButton(onClick = onChooseAnother, label = "choose another key")
                        PassSecondaryButton(onClick = onClose, label = "close")
                    }
                }

                ModalPhase.SUCCESS -> {
                    PassPrimaryButton(onClick = onContinue, label = "continue")
                }
            }
        },
    )
}

private fun ModalPhase.title() =
    when (this) {
        ModalPhase.RUNNING -> "checking gpg key"
        ModalPhase.FAILED -> "import failed"
        ModalPhase.SUCCESS -> "key imported"
    }

@Composable
private fun GpgImportChecklistRowContent(row: ChecklistGroup) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChecklistStatus(status = row.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.label, style = PassType.Body, color = row.status.color())
            row.detail?.let { detail ->
                Text(
                    detail,
                    style = PassType.Caption,
                    color =
                        if (row.status ==
                            StepStatus.FAILED
                        ) {
                            PassColorsDark.Danger
                        } else {
                            PassColorsDark.TextFaint
                        },
                )
            }
        }
    }
}

@Composable
private fun ChecklistStatus(status: StepStatus) {
    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (status) {
            StepStatus.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = PassColorsDark.Accent,
                    strokeWidth = 2.dp,
                )
            }

            StepStatus.PASSED -> {
                Text("✓", style = PassType.Body, color = PassColorsDark.Accent)
            }

            StepStatus.FAILED -> {
                Text("!", style = PassType.Body, color = PassColorsDark.Danger)
            }

            StepStatus.NEUTRAL -> {
                Text("i", style = PassType.Caption, color = PassColorsDark.TextFaint)
            }

            StepStatus.NOT_CHECKED -> {
                Text("•", style = PassType.Caption, color = PassColorsDark.TextFaint)
            }
        }
    }
}

private fun StepStatus.color() =
    when (this) {
        StepStatus.FAILED -> PassColorsDark.Danger

        StepStatus.NOT_CHECKED,
        StepStatus.NEUTRAL,
        -> PassColorsDark.TextDim

        StepStatus.RUNNING,
        StepStatus.PASSED,
        -> PassColorsDark.TextPrimary
    }

// ---- Previews ----

private const val PREVIEW_BG = 0xFF0B0D0BL

@Composable
private fun GpgImportChecklistDialogPreview(
    phase: ModalPhase,
    groups: List<ChecklistGroup>,
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
                phase.title(),
                style = PassType.Title,
                color = if (phase == ModalPhase.FAILED) PassColorsDark.Danger else PassColorsDark.TextPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { row -> GpgImportChecklistRowContent(row) }
                if (phase == ModalPhase.SUCCESS) {
                    Text("your gpg key is ready to decrypt your store.", style = PassType.Caption, color = PassColorsDark.TextDim)
                }
            }
            when (phase) {
                ModalPhase.RUNNING -> {
                    PassSecondaryButton(onClick = onCancel, label = "cancel")
                }

                ModalPhase.FAILED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PassPrimaryButton(onClick = onChooseAnother, label = "choose another key")
                        PassSecondaryButton(onClick = onClose, label = "close")
                    }
                }

                ModalPhase.SUCCESS -> {
                    PassPrimaryButton(onClick = onContinue, label = "continue")
                }
            }
        }
    }
}

@Preview(name = "gpg import · running", showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360)
@Composable
private fun GpgImportDialogRunningPreview() {
    PassTheme {
        GpgImportChecklistDialogPreview(
            phase = ModalPhase.RUNNING,
            groups = previewGroups(runningGroup = ChecklistGroupId.PASSPHRASE_PROTECTED),
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
            phase = ModalPhase.FAILED,
            groups =
                previewGroups(
                    failedGroup = ChecklistGroupId.SECRET_KEY_RECOGNIZED,
                    errorMessage =
                        "this doesn't look like an armored gpg secret key. check you exported a private key, not a public one.",
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
            phase = ModalPhase.FAILED,
            groups =
                previewGroups(
                    failedGroup = ChecklistGroupId.PASSPHRASE_PROTECTED,
                    errorMessage =
                        "this key is not passphrase-protected (1A2B3C4D). " +
                            "re-export it with a passphrase — at-rest security depends on it.",
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
            phase = ModalPhase.SUCCESS,
            groups = previewGroups(authSubkeyFound = true, stored = true),
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
            phase = ModalPhase.SUCCESS,
            groups = previewGroups(authSubkeyFound = false, stored = true),
            onCancel = {},
            onClose = {},
            onChooseAnother = {},
            onContinue = {},
        )
    }
}

private fun previewGroups(
    runningGroup: ChecklistGroupId? = null,
    failedGroup: ChecklistGroupId? = null,
    errorMessage: String? = null,
    authSubkeyFound: Boolean? = null,
    stored: Boolean = false,
): List<ChecklistGroup> {
    val order =
        listOf(
            ChecklistGroupId.SECRET_KEY_RECOGNIZED,
            ChecklistGroupId.DECRYPTION_KEY_USABLE,
            ChecklistGroupId.PASSPHRASE_PROTECTED,
            ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE,
            ChecklistGroupId.STORED_SECURELY,
        )
    return order.map { id ->
        val status =
            when {
                id == runningGroup -> StepStatus.RUNNING
                id == failedGroup -> StepStatus.FAILED
                failedGroup != null && order.indexOf(id) > order.indexOf(failedGroup) -> StepStatus.NOT_CHECKED
                id == ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE && authSubkeyFound != null ->
                    if (authSubkeyFound) StepStatus.PASSED else StepStatus.NEUTRAL
                id == ChecklistGroupId.STORED_SECURELY && stored -> StepStatus.PASSED
                runningGroup != null && order.indexOf(id) < order.indexOf(runningGroup) -> StepStatus.PASSED
                runningGroup != null -> StepStatus.NOT_CHECKED
                failedGroup != null -> StepStatus.PASSED
                authSubkeyFound != null && id != ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE -> StepStatus.PASSED
                else -> StepStatus.NOT_CHECKED
            }
        ChecklistGroup(
            id = id,
            label = id.previewLabel(status),
            detail = id.previewDetail(status, errorMessage),
            status = status,
        )
    }
}

private fun ChecklistGroupId.previewLabel(status: StepStatus): String =
    when (this) {
        ChecklistGroupId.SECRET_KEY_RECOGNIZED -> "secret key recognized"
        ChecklistGroupId.DECRYPTION_KEY_USABLE -> "decryption key usable"
        ChecklistGroupId.PASSPHRASE_PROTECTED -> "passphrase protected"
        ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE ->
            if (status == StepStatus.NEUTRAL) "github ssh key not reusable" else "github ssh key reusable"
        ChecklistGroupId.STORED_SECURELY -> "stored securely"
    }

private fun ChecklistGroupId.previewDetail(
    status: StepStatus,
    errorMessage: String?,
): String? =
    when (status) {
        StepStatus.FAILED -> errorMessage
        StepStatus.RUNNING,
        StepStatus.NEUTRAL,
        ->
            when (this) {
                ChecklistGroupId.SECRET_KEY_RECOGNIZED -> "looks like an openpgp secret key ring"
                ChecklistGroupId.DECRYPTION_KEY_USABLE -> "encryption subkey includes secret material"
                ChecklistGroupId.PASSPHRASE_PROTECTED -> "private key material is protected by a passphrase"
                ChecklistGroupId.GITHUB_SSH_KEY_REUSABLE -> "only ed25519 [A] subkeys can be reused for github ssh"
                ChecklistGroupId.STORED_SECURELY -> "save the protected key ring in encrypted app storage"
            }

        StepStatus.NOT_CHECKED,
        StepStatus.PASSED,
        -> null
    }
