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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
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
        if (state.gpgImported) {
            Spacer(Modifier.height(4.dp))
            Text("Key imported successfully.", color = PassColorsDark.Accent, style = PassType.Caption)
        }
        Spacer(Modifier.height(16.dp))
        PassPrimaryButton(
            onClick = viewModel::importGpgKey,
            label = "> gpg --import",
            enabled = state.gpgKeyText.isNotBlank() && !state.gpgImported,
        )
        Spacer(Modifier.height(8.dp))
        PassPrimaryButton(
            onClick = onNext,
            label = "> git init",
            enabled = state.gpgImported,
        )
    }

    state.gpgImportError?.let { err ->
        GpgImportErrorDialog(
            error = err,
            onDismiss = viewModel::dismissImportError,
        )
    }
}

@Composable
private fun GpgImportErrorDialog(
    error: GpgImportError,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PassColorsDark.Surface,
        titleContentColor = PassColorsDark.Danger,
        textContentColor = PassColorsDark.TextDim,
        title = { Text(error.title, style = PassType.Title) },
        text = { Text(error.message, style = PassType.Caption, color = PassColorsDark.TextDim) },
        confirmButton = {
            PassPrimaryButton(onClick = onDismiss, label = "got it")
        },
    )
}
