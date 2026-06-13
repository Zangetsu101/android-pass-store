package com.zangetsu101.pass.onboarding

import android.content.ClipData
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassSecondaryButton
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SSH_KEY_SHIMMER_MIN_MS = 2000L

@Composable
fun CloneRepoScreen(
    viewModel: CloneRepoViewModel,
    onNext: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showShimmer by remember { mutableStateOf(true) }
    var showPassphraseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SSH_KEY_SHIMMER_MIN_MS)
        showShimmer = false
    }

    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let { url ->
            viewModel.clearNavigation()
            onNext(url)
        }
    }

    if (showPassphraseDialog) {
        GpgPassphraseDialog(
            passphraseError = state.passphraseError,
            isLoading = state.isExtracting,
            onConfirm = { passphrase ->
                viewModel.onClone(passphrase)
            },
            onDismiss = {
                if (!state.isExtracting) showPassphraseDialog = false
            },
        )
        LaunchedEffect(state.navigateTo) {
            if (state.navigateTo != null) showPassphraseDialog = false
        }
    }

    OnboardingScaffold(
        step = 2,
        total = 3,
        title = "clone store",
        subtitle = "point to your existing pass git repository",
    ) {
        Text("git remote url", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        PassTextField(
            value = state.remoteUrl,
            onValueChange = viewModel::setRemoteUrl,
            placeholder = "git@github.com:user/pass.git",
            isError = state.remoteUrlError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        state.remoteUrlError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = PassType.Caption, color = PassColorsDark.Danger)
        }
        Spacer(Modifier.height(16.dp))

        if (state.authSubkey != null) {
            SourceToggle(
                source = state.source,
                onSourceChange = viewModel::setSource,
            )
            Spacer(Modifier.height(12.dp))
        }

        Text("ssh public key", style = PassType.Label)
        Spacer(Modifier.height(6.dp))

        val authSubkey = state.authSubkey
        when {
            state.source == SshKeySource.GPG_AUTH && authSubkey != null -> {
                GpgAuthKeyCard(
                    authSubkey = authSubkey,
                    onCopy = { key ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", key)))
                        }
                    },
                )
            }

            else -> {
                DeviceKeyCard(
                    publicKey = state.devicePublicKey,
                    showShimmer = showShimmer || state.devicePublicKey == null,
                    onCopy = { key ->
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", key)))
                        }
                    },
                    onRegenerate = {
                        scope.launch {
                            showShimmer = true
                            viewModel.regenerateSshKey()
                            delay(SSH_KEY_SHIMMER_MIN_MS)
                            showShimmer = false
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PassColorsDark.Warning.copy(alpha = 0.07f), PassShapes.small)
                    .border(1.dp, PassColorsDark.Warning.copy(alpha = 0.27f), PassShapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "⚠ github → Settings → SSH and GPG Keys → New SSH Key",
                style = PassType.Caption,
                color = PassColorsDark.Warning.copy(alpha = 0.8f),
                lineHeight = PassType.Caption.fontSize * 1.6,
            )
        }
        Spacer(Modifier.height(20.dp))
        PassPrimaryButton(
            onClick = {
                if (viewModel.validateRemoteUrl()) {
                    if (state.source == SshKeySource.GPG_AUTH) {
                        showPassphraseDialog = true
                    } else {
                        viewModel.onClone()
                    }
                }
            },
            label = "> pass import-key",
        )
    }
}

@Composable
private fun SourceToggle(
    source: SshKeySource,
    onSourceChange: (SshKeySource) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SourceTabButton(
            label = "gpg auth key",
            selected = source == SshKeySource.GPG_AUTH,
            onClick = { onSourceChange(SshKeySource.GPG_AUTH) },
            modifier = Modifier.weight(1f),
        )
        SourceTabButton(
            label = "generate on device",
            selected = source == SshKeySource.DEVICE,
            onClick = { onSourceChange(SshKeySource.DEVICE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .background(
                    if (selected) PassColorsDark.AccentDim else PassColorsDark.Surface,
                    RoundedCornerShape(6.dp),
                ).border(
                    1.dp,
                    if (selected) PassColorsDark.AccentMid else PassColorsDark.Surface,
                    RoundedCornerShape(6.dp),
                ).padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PassType.Caption,
            color = if (selected) PassColorsDark.Accent else PassColorsDark.TextDim,
        )
    }
}

@Composable
private fun GpgAuthKeyCard(
    authSubkey: com.zangetsu101.pass.keymanagement.AuthSubkeyInfo,
    onCopy: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ed25519 · from gpg auth subkey", style = PassType.Caption, color = PassColorsDark.TextDim)
            Box(
                modifier =
                    Modifier
                        .background(PassColorsDark.AccentDim, RoundedCornerShape(3.dp))
                        .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("A", style = PassType.Caption, color = PassColorsDark.Accent)
            }
        }
        Text(
            text = authSubkey.sshPublicKey,
            style = PassType.Caption,
            color = PassColorsDark.TextPrimary,
            lineHeight = PassType.Caption.fontSize * 1.7,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(authSubkey.sshFingerprint, style = PassType.Caption, color = PassColorsDark.TextFaint)
            Text(authSubkey.uid, style = PassType.Caption, color = PassColorsDark.TextFaint)
        }
    }
    Spacer(Modifier.height(8.dp))
    PassPrimaryButton(
        onClick = { onCopy(authSubkey.sshPublicKey) },
        label = "copy key",
    )
}

@Composable
private fun DeviceKeyCard(
    publicKey: String?,
    showShimmer: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("ed25519 · generated on device", style = PassType.Caption, color = PassColorsDark.TextDim)
        if (publicKey == null || showShimmer) {
            SshKeyShimmer()
        } else {
            Text(
                text = publicKey,
                style = PassType.Caption,
                color = PassColorsDark.TextPrimary,
                lineHeight = PassType.Caption.fontSize * 1.7,
            )
        }
        Text("pass-android@device", style = PassType.Caption, color = PassColorsDark.TextDim)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PassPrimaryButton(
            onClick = { if (publicKey != null) onCopy(publicKey) },
            enabled = publicKey != null,
            label = "copy key",
            modifier = Modifier.weight(1f),
        )
        PassSecondaryButton(
            onClick = onRegenerate,
            label = "regenerate",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GpgPassphraseDialog(
    passphraseError: String?,
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PassColorsDark.Surface,
        titleContentColor = PassColorsDark.TextPrimary,
        textContentColor = PassColorsDark.TextDim,
        title = { Text("gpg passphrase", style = PassType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "enter passphrase to extract the auth subkey",
                    style = PassType.Caption,
                    color = PassColorsDark.TextFaint,
                )
                PassTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    placeholder = "passphrase",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { if (passphrase.isNotEmpty()) onConfirm(passphrase) }),
                    isError = passphraseError != null,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                passphraseError?.let {
                    Text(it, style = PassType.Caption, color = PassColorsDark.Danger)
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PassColorsDark.Accent,
                )
            } else {
                TextButton(
                    onClick = { if (passphrase.isNotEmpty()) onConfirm(passphrase) },
                    enabled = passphrase.isNotEmpty(),
                ) {
                    Text("confirm", color = PassColorsDark.Accent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("cancel", color = PassColorsDark.TextDim)
            }
        },
    )
}

@Composable
private fun SshKeyShimmer() {
    val captionHeight = with(LocalDensity.current) { PassType.Caption.fontSize.toDp() }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    val shimmerBrush =
        Brush.linearGradient(
            colors = listOf(PassColorsDark.Surface, PassColorsDark.Raised, PassColorsDark.Surface),
            start = Offset(offsetX, 0f),
            end = Offset(offsetX + 300f, 0f),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 140.dp, height = captionHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth().height(9.dp).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.size(width = 100.dp, height = captionHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
    }
}
