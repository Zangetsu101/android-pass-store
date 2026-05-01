package com.example.pass.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.passTextFieldColors
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@Composable
fun OnboardingRemoteUrlScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    OnboardingScaffold(step = 1, total = 4, title = "connect your repository") {
        OutlinedTextField(
            value = state.remoteUrl,
            onValueChange = viewModel::setRemoteUrl,
            label = { Text("git remote url", style = PassType.Caption) },
            placeholder = { Text("git@github.com:user/pass.git", style = PassType.Caption) },
            singleLine = true,
            isError = state.remoteUrlError != null,
            supportingText = state.remoteUrlError?.let { { Text(it, style = PassType.Caption) } },
            colors = passTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PassPrimaryButton(
            onClick = { if (viewModel.validateRemoteUrl()) onNext() },
            label = "$ next",
        )
    }
}

@Composable
fun OnboardingSshKeyScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.generateSshKeyIfNeeded() }

    OnboardingScaffold(step = 2, total = 4, title = "ssh public key") {
        Text(
            text = "Add this public key to your git server before continuing.",
            style = PassType.Body,
        )
        Spacer(Modifier.height(16.dp))
        val key = state.sshPublicKey
        if (key == null) {
            CircularProgressIndicator(color = PassColorsDark.Accent)
        } else {
            Text(
                text = key,
                style = PassType.Caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
            Spacer(Modifier.height(8.dp))
            PassSecondaryButton(
                onClick = { clipboard.setText(AnnotatedString(key)) },
                label = "copy to clipboard",
            )
            Spacer(Modifier.height(8.dp))
            PassPrimaryButton(onClick = onNext, label = "$ next")
        }
    }
}

@Composable
fun OnboardingGpgImportScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)
                ?.bufferedReader()
                ?.readText()
                ?: return@let
            viewModel.setGpgKeyText(text)
        }
    }

    OnboardingScaffold(step = 3, total = 4, title = "import gpg key") {
        Text(
            text = "This is the key used to decrypt your pass store. It must match the key that encrypted the .gpg files in your repository.",
            style = PassType.Body,
        )
        Spacer(Modifier.height(12.dp))
        Text(text = "EXPORT FROM DESKTOP", style = PassType.Label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "gpg --armor --export-secret-keys YOUR_KEY_ID > key.asc",
            style = PassType.Caption,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Transfer key.asc to your phone, then pick the file below or paste the contents directly.",
            style = PassType.Caption,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your key must be passphrase-protected. You'll enter it when starting a session.",
            style = PassType.Caption,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.gpgKeyText,
            onValueChange = viewModel::setGpgKeyText,
            label = { Text("armored gpg private key", style = PassType.Caption) },
            placeholder = { Text("-----BEGIN PGP PRIVATE KEY BLOCK-----", style = PassType.Caption) },
            minLines = 5,
            maxLines = 10,
            isError = state.gpgImportError != null,
            colors = passTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        PassSecondaryButton(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            label = "pick file…",
        )
        state.gpgImportError?.let { err ->
            Spacer(Modifier.height(4.dp))
            Text(err, color = PassColorsDark.Danger, style = PassType.Caption)
        }
        if (state.gpgImported) {
            Spacer(Modifier.height(4.dp))
            Text("Key imported successfully.", color = PassColorsDark.Accent, style = PassType.Caption)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PassPrimaryButton(
                onClick = viewModel::importGpgKey,
                label = "import",
                enabled = state.gpgKeyText.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            PassPrimaryButton(
                onClick = onNext,
                label = "$ next",
                enabled = state.gpgImported,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun OnboardingCloneScreen(
    viewModel: OnboardingViewModel,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.startClone() }

    LaunchedEffect(state.cloneComplete) {
        if (state.cloneComplete) onSuccess()
    }

    OnboardingScaffold(step = 4, total = 4, title = "cloning repository") {
        when {
            state.cloning -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PassColorsDark.Accent)
                        Spacer(Modifier.height(16.dp))
                        Text("cloning…", style = PassType.Body)
                    }
                }
            }
            state.cloneError != null -> {
                Text(state.cloneError!!, color = PassColorsDark.Danger, style = PassType.Body)
                Spacer(Modifier.height(24.dp))
                PassPrimaryButton(onClick = { viewModel.startClone() }, label = "$ retry")
            }
            else -> {
                CircularProgressIndicator(color = PassColorsDark.Accent)
            }
        }
    }
}

@Composable
private fun OnboardingScaffold(
    step: Int,
    total: Int,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "SETUP · $step / $total",
            style = PassType.Caption,
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = PassType.Title)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun PassPrimaryButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = PassColorsDark.AccentDim,
            contentColor = PassColorsDark.Accent,
            disabledContainerColor = PassColorsDark.Border,
            disabledContentColor = PassColorsDark.TextFaint,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, if (enabled) PassColorsDark.Accent else PassColorsDark.Border, MaterialTheme.shapes.small),
    ) {
        Text(label, style = PassType.Body.copy(color = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint))
    }
}

@Composable
private fun PassSecondaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PassColorsDark.TextDim,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, PassColorsDark.Border2),
        modifier = modifier.fillMaxWidth().height(40.dp),
    ) {
        Text(label, style = PassType.Body.copy(color = PassColorsDark.TextDim))
    }
}

