package com.example.pass.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.pass.R
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.components.PassSecondaryButton
import com.example.pass.ui.components.passTextFieldColors
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassShapes
import com.example.pass.ui.theme.PassType

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(top = 40.dp)) {
                // Logo mark
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PassColorsDark.AccentDim, RoundedCornerShape(8.dp))
                        .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = PassColorsDark.Accent,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = buildAnnotatedString {
                        append("pass")
                        withStyle(SpanStyle(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light)) {
                            append(".android")
                        }
                    },
                    style = PassType.Display,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "the standard unix password\nmanager — on your phone",
                    style = PassType.Body
                )
                Spacer(Modifier.height(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow("gpg", "end-to-end encrypted with your gpg key")
                    FeatureRow("git", "syncs with any git remote")
                    FeatureRow("pass", "100% compatible with unix pass")
                }
            }
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                PassPrimaryButton(onClick = onStart, label = "$ clone a store")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "requires git + gpg key",
                    style = PassType.Caption,
                    color = PassColorsDark.TextFaint,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(tag: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .background(PassColorsDark.AccentDim, RoundedCornerShape(3.dp))
                .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(tag, style = PassType.Label)
        }
        Text(description, style = PassType.Body)
    }
}

private const val SSH_KEY_SHIMMER_MIN_MS = 2000L

@Composable
fun CloneRepoScreen(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showShimmer by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.generateSshKeyIfNeeded()
        delay(SSH_KEY_SHIMMER_MIN_MS)
        showShimmer = false
    }

    OnboardingScaffold(
        step = 1,
        total = 2,
        title = "clone store",
        subtitle = "point to your existing pass git repository",
    ) {
        Text("git remote url", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.remoteUrl,
            onValueChange = viewModel::setRemoteUrl,
            placeholder = { Text("git@github.com:user/pass.git", style = PassType.Caption) },
            singleLine = true,
            isError = state.remoteUrlError != null,
            supportingText = state.remoteUrlError?.let { { Text(it, style = PassType.Caption) } },
            textStyle = PassType.Body,
            colors = passTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("ssh public key", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        val key = state.sshPublicKey
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("ed25519 · generated on device", style = PassType.Caption, color = PassColorsDark.TextDim)
            if (key == null || showShimmer) {
              SshKeyShimmer()
            } else {
              Text(
                text = key,
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
                onClick = { if (key != null) clipboard.setText(AnnotatedString(key)) },
                enabled = key != null,
                label = "copy key",
                modifier = Modifier.weight(1f),
            )
            PassSecondaryButton(
                onClick = {
                    scope.launch {
                        showShimmer = true
                        viewModel.regenerateSshKey()
                        delay(SSH_KEY_SHIMMER_MIN_MS)
                        showShimmer = false
                    }
                },
                label = "regenerate",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
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
            onClick = { if (viewModel.validateRemoteUrl()) onNext() },
            label = "$ next",
        )
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

    OnboardingScaffold(step = 2, total = 2, title = "import gpg key") {
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
            textStyle = PassType.Body,
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
fun CloneProgressScreen(
    viewModel: OnboardingViewModel,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val logState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.startClone() }
    LaunchedEffect(state.cloneComplete) { if (state.cloneComplete) onSuccess() }
    LaunchedEffect(state.cloneLog.size) {
        if (state.cloneLog.isNotEmpty()) logState.animateScrollToItem(state.cloneLog.size - 1)
    }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text("CLONING REPOSITORY", style = PassType.Label)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface, PassShapes.medium)
                    .border(1.dp, PassColorsDark.Border, PassShapes.medium)
                    .padding(12.dp),
            ) {
                LazyColumn(state = logState, modifier = Modifier.fillMaxSize()) {
                    items(state.cloneLog) { line ->
                        Text("> $line", style = PassType.Caption, color = PassColorsDark.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.cloning) {
                LinearProgressIndicator(
                    progress = { state.cloneProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = PassColorsDark.Accent,
                    trackColor = PassColorsDark.Border,
                )
            }
            state.cloneError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(err, color = PassColorsDark.Danger, style = PassType.Caption)
                Spacer(Modifier.height(8.dp))
                PassPrimaryButton(onClick = { viewModel.startClone() }, label = "$ retry")
            }
            if (state.cloning) {
                Spacer(Modifier.height(12.dp))
                PassSecondaryButton(
                    onClick = { viewModel.cancelClone() },
                    label = "cancel",
                )
            }
        }
    }
}

@Composable
private fun OnboardingScaffold(
    step: Int,
    total: Int,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "SETUP · $step / $total", style = PassType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(total) { i ->
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = 3.dp)
                                .background(
                                    PassColorsDark.Accent.copy(alpha = if (i < step) 1f else 0.3f),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = PassType.Title)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = PassType.Caption, lineHeight = PassType.Caption.fontSize * 1.6)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
private fun SshKeyShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(PassColorsDark.Surface, PassColorsDark.Raised, PassColorsDark.Surface),
        start = Offset(offsetX, 0f),
        end = Offset(offsetX + 300f, 0f),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PassColorsDark.Surface, PassShapes.small)
            .border(1.dp, PassColorsDark.Border2, PassShapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 140.dp, height = 9.dp).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth().height(9.dp).background(shimmerBrush, RoundedCornerShape(3.dp)))
        Box(Modifier.size(width = 100.dp, height = 9.dp).background(shimmerBrush, RoundedCornerShape(3.dp)))
    }
}
