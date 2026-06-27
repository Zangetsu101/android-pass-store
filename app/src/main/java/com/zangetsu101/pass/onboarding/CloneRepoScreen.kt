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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassSecondaryButton
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassTheme
import com.zangetsu101.pass.ui.theme.PassType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SSH_KEY_SHIMMER_MIN_MS = 2000L

@Composable
fun CloneRepoScreen(
    viewModel: CloneRepoViewModel,
    onNext: (String) -> Unit,
) {
    val form by viewModel.form.collectAsState()
    val keyResolution by viewModel.keyResolution.collectAsState()
    val deviceKey by viewModel.deviceKey.collectAsState()
    val extraction by viewModel.extraction.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showShimmer by remember { mutableStateOf(true) }
    var showPassphraseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(timeMillis = SSH_KEY_SHIMMER_MIN_MS)
        showShimmer = false
    }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { url ->
            showPassphraseDialog = false
            onNext(url)
        }
    }

    if (showPassphraseDialog) {
        GpgPassphraseDialog(
            passphraseError = (extraction as? Extraction.Failed)?.reason,
            isLoading = extraction is Extraction.Extracting,
            onConfirm = { passphrase ->
                viewModel.onClone(passphrase)
            },
            onDismiss = {
                if (extraction !is Extraction.Extracting) showPassphraseDialog = false
            },
        )
    }

    CloneRepoContent(
        form = form,
        keyResolution = keyResolution,
        deviceKey = deviceKey,
        showShimmer = showShimmer,
        onRemoteUrlChange = viewModel::setRemoteUrl,
        onSourceChange = viewModel::setSource,
        onCopy = { key ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", key)))
            }
        },
        onRegenerate = {
            scope.launch {
                showShimmer = true
                viewModel.regenerateSshKey()
                delay(timeMillis = SSH_KEY_SHIMMER_MIN_MS)
                showShimmer = false
            }
        },
        onSubmit = {
            if (viewModel.validateRemoteUrl()) {
                val k = keyResolution
                if (k is KeyResolution.GpgAvailable && k.selectedSource == SshKeySource.GPG_AUTH) {
                    showPassphraseDialog = true
                } else {
                    viewModel.onClone()
                }
            }
        },
    )
}

@Composable
private fun CloneRepoContent(
    form: FormState,
    keyResolution: KeyResolution,
    deviceKey: DeviceKey,
    showShimmer: Boolean,
    onRemoteUrlChange: (String) -> Unit,
    onSourceChange: (SshKeySource) -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onSubmit: () -> Unit,
) {
    val gpg = keyResolution as? KeyResolution.GpgAvailable
    val isResolving = keyResolution is KeyResolution.Resolving
    val useGpgKey = gpg != null && gpg.selectedSource == SshKeySource.GPG_AUTH

    OnboardingScaffold(
        step = 2,
        total = 3,
        title = "clone store",
        subtitle = "point to your existing pass git repository",
    ) {
        Text("git remote url", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        PassTextField(
            value = form.remoteUrl,
            onValueChange = onRemoteUrlChange,
            placeholder = "git@github.com:user/pass-store",
            isError = form.remoteUrlError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        form.remoteUrlError?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = PassType.Caption, color = PassColorsDark.Danger)
        }
        Spacer(Modifier.height(16.dp))

        Text("ssh auth · source", style = PassType.Label)
        Spacer(Modifier.height(6.dp))

        when (keyResolution) {
            KeyResolution.Resolving -> {
                SourceTogglePlaceholder()
            }

            is KeyResolution.GpgAvailable -> {
                SourceToggle(
                    source = keyResolution.selectedSource,
                    gpgEnabled = true,
                    onSourceChange = onSourceChange,
                )
            }

            KeyResolution.DeviceOnly -> {
                SourceToggle(
                    source = SshKeySource.DEVICE,
                    gpgEnabled = false,
                    onSourceChange = onSourceChange,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (useGpgKey) {
            GpgAuthKeyCard(
                authSubkey = gpg.authKey,
                onCopy = onCopy,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            val publicKey = (deviceKey as? DeviceKey.Ready)?.publicKey
            DeviceKeyCard(
                publicKey = publicKey,
                showShimmer = showShimmer || deviceKey !is DeviceKey.Ready,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
            )
        }

        Spacer(Modifier.height(14.dp))
        Banner()
        Spacer(Modifier.height(20.dp))
        PassPrimaryButton(
            onClick = onSubmit,
            label = "> git clone",
            enabled = !isResolving,
        )
    }
}

@Composable
private fun Banner() {
    val color = PassColorsDark.Warning
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.07f), PassShapes.small)
                .border(1.dp, color.copy(alpha = 0.27f), PassShapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "⚠ github → repo → settings → deploy keys → add deploy key · " +
                          "enable “Allow write access”",
            style = PassType.Caption,
            color = color.copy(alpha = 0.8f),
            lineHeight = PassType.Caption.fontSize * 1.6,
        )
    }
}

@Composable
private fun SourceToggle(
    source: SshKeySource,
    gpgEnabled: Boolean,
    onSourceChange: (SshKeySource) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        SourceTabButton(
            label = "use gpg auth key",
            selected = source == SshKeySource.GPG_AUTH,
            enabled = gpgEnabled,
            onClick = { onSourceChange(SshKeySource.GPG_AUTH) },
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(PassColorsDark.Border2))
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
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .background(if (selected) PassColorsDark.AccentDim else Color.Transparent)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PassType.Caption,
            color =
                when {
                    !enabled -> PassColorsDark.TextFaint
                    selected -> PassColorsDark.Accent
                    else -> PassColorsDark.TextDim
                },
        )
    }
}

@Composable
private fun SourceTogglePlaceholder() {
    val brush = rememberShimmerBrush()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        repeat(2) { i ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Invisible glyph reserves the real Caption line-box height so the
                // placeholder matches SourceTabButton's height exactly.
                Text(text = " ", style = PassType.Caption, color = Color.Transparent)
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight()
                        .background(brush, RoundedCornerShape(3.dp)),
                )
            }
            if (i == 0) Box(Modifier.width(1.dp).fillMaxHeight().background(PassColorsDark.Border2))
        }
    }
}

@Composable
private fun GpgAuthKeyCard(
    authSubkey: AuthSubkeyInfo,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ed25519 · from gpg auth subkey", style = PassType.Caption, color = PassColorsDark.TextDim)
            Text("[A]", style = PassType.Caption, color = PassColorsDark.Accent)
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PassPrimaryButton(
            onClick = { onCopy(authSubkey.sshPublicKey) },
            label = "copy key",
            modifier = Modifier.weight(1f),
        )
        PassSecondaryButton(
            onClick = {},
            label = "regenerate",
            enabled = false,
            modifier = Modifier.weight(1f),
        )
    }
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
            enabled = publicKey != null,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
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
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    return Brush.linearGradient(
        colors = listOf(PassColorsDark.Surface, PassColorsDark.Raised, PassColorsDark.Surface),
        start = Offset(offsetX, 0f),
        end = Offset(offsetX + 300f, 0f),
    )
}

@Composable
private fun SshKeyShimmer() {
    // Match the real key Text's line box (lineHeight = Caption.fontSize * 1.7) so the
    // card doesn't jump height when the key loads.
    val lineHeight = with(LocalDensity.current) { (PassType.Caption.fontSize * 1.7).toDp() }
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small),
    ) {
        Box(Modifier.size(width = 140.dp, height = lineHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth().height(lineHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.size(width = 100.dp, height = lineHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth().height(lineHeight).background(shimmerBrush, RoundedCornerShape(3.dp)))
    }
}

// ---- Previews ----
// Top-level CloneRepoScreen takes a ViewModel, so the stateless CloneRepoContent
// is previewed full-screen instead.

private const val PREVIEW_BG = 0xFF0B0D0BL

private val previewAuthSubkey =
    AuthSubkeyInfo(
        keyId = 0xDEADBEEFL,
        sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILExampleKeyDataForPreview0123456789abcd",
        sshFingerprint = "SHA256:1a2b3c4d5e6f7g8h9i0jExampleFingerprintForPreview",
        uid = "Tameem <tameem@opencrvs.org>",
        created = 0L,
    )

@Preview(name = "Clone repo · device key", showBackground = true, backgroundColor = PREVIEW_BG)
@Composable
private fun CloneRepoContentDevicePreview() {
    PassTheme {
        CloneRepoContent(
            form = FormState(),
            keyResolution = KeyResolution.DeviceOnly,
            deviceKey = DeviceKey.Ready(previewAuthSubkey.sshPublicKey),
            showShimmer = false,
            onRemoteUrlChange = {},
            onSourceChange = {},
            onCopy = {},
            onRegenerate = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Clone repo · loading", showBackground = true, backgroundColor = PREVIEW_BG)
@Composable
private fun CloneRepoContentLoadingPreview() {
    PassTheme {
        CloneRepoContent(
            form = FormState(),
            keyResolution = KeyResolution.Resolving,
            deviceKey = DeviceKey.None,
            showShimmer = true,
            onRemoteUrlChange = {},
            onSourceChange = {},
            onCopy = {},
            onRegenerate = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Clone repo · generate on device", showBackground = true, backgroundColor = PREVIEW_BG)
@Composable
private fun CloneRepoContentGeneratePreview() {
    PassTheme {
        CloneRepoContent(
            form = FormState(),
            keyResolution = KeyResolution.GpgAvailable(previewAuthSubkey, SshKeySource.DEVICE),
            deviceKey = DeviceKey.Ready(previewAuthSubkey.sshPublicKey),
            showShimmer = false,
            onRemoteUrlChange = {},
            onSourceChange = {},
            onCopy = {},
            onRegenerate = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Clone repo · gpg auth key", showBackground = true, backgroundColor = PREVIEW_BG)
@Composable
private fun CloneRepoContentGpgPreview() {
    PassTheme {
        CloneRepoContent(
            form = FormState(),
            keyResolution = KeyResolution.GpgAvailable(previewAuthSubkey, SshKeySource.GPG_AUTH),
            deviceKey = DeviceKey.None,
            showShimmer = false,
            onRemoteUrlChange = {},
            onSourceChange = {},
            onCopy = {},
            onRegenerate = {},
            onSubmit = {},
        )
    }
}
