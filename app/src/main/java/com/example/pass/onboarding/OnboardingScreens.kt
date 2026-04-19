package com.example.pass.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingRemoteUrlScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    OnboardingScaffold(step = 1, total = 5, title = "Connect your repository") {
        OutlinedTextField(
            value = state.remoteUrl,
            onValueChange = viewModel::setRemoteUrl,
            label = { Text("Git remote URL") },
            placeholder = { Text("git@github.com:user/pass.git") },
            singleLine = true,
            isError = state.remoteUrlError != null,
            supportingText = state.remoteUrlError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (viewModel.validateRemoteUrl()) onNext() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
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

    OnboardingScaffold(step = 2, total = 5, title = "Your SSH public key") {
        Text(
            text = "Add this public key to your git server before continuing.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        val key = state.sshPublicKey
        if (key == null) {
            CircularProgressIndicator()
        } else {
            Text(
                text = key,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(key)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Copy to clipboard")
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
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

    OnboardingScaffold(step = 3, total = 5, title = "Import your GPG key") {
        Text(
            text = "This is the key used to decrypt your pass store. It must be the same key that encrypted the .gpg files in your repository.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Export from desktop",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "gpg --armor --export-secret-keys YOUR_KEY_ID > key.asc",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Transfer key.asc to your phone, then tap \"Pick file\" below, or paste the contents directly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "If your key has a passphrase, enter it below — it will be stripped and re-protected by the device Keystore so you won't need it again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.gpgKeyText,
            onValueChange = viewModel::setGpgKeyText,
            label = { Text("Armored GPG private key") },
            placeholder = { Text("-----BEGIN PGP PRIVATE KEY BLOCK-----") },
            minLines = 5,
            maxLines = 10,
            isError = state.gpgImportError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pick file…")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.gpgPassphrase,
            onValueChange = viewModel::setGpgPassphrase,
            label = { Text("Passphrase (leave blank if none)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.gpgImportError?.let { err ->
            Spacer(Modifier.height(4.dp))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.gpgImported) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Key imported successfully.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::importGpgKey,
                modifier = Modifier.weight(1f),
                enabled = state.gpgKeyText.isNotBlank(),
            ) {
                Text("Import")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f),
                enabled = state.gpgImported,
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun OnboardingBiometricScreen(
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkEnrolled() = BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    var enrolled by remember { mutableStateOf(checkEnrolled()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enrolled = checkEnrolled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingScaffold(step = 4, total = 5, title = "Biometric unlock") {
        if (enrolled) {
            Text(
                "Biometrics are set up. PassDroid will use them to protect your keys.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Next")
            }
        } else {
            Text(
                "No biometric or device credential is enrolled. Please set up a screen lock or fingerprint in your device settings.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Security Settings")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
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

    OnboardingScaffold(step = 5, total = 5, title = "Cloning repository") {
        when {
            state.cloning -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Cloning…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.cloneError != null -> {
                Text(
                    "Clone failed: ${state.cloneError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startClone() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry")
                }
            }
            else -> {
                CircularProgressIndicator()
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Step $step of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        content()
    }
}
