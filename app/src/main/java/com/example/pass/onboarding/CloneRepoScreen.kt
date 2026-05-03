package com.example.pass.onboarding

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassSecondaryButton
import com.example.pass.ui.components.PassTextField
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassShapes
import com.example.pass.ui.theme.PassType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SSH_KEY_SHIMMER_MIN_MS = 2000L

@Composable
fun CloneRepoScreen(
    viewModel: CloneRepoViewModel,
    onNext: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showShimmer by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(SSH_KEY_SHIMMER_MIN_MS)
        showShimmer = false
    }

    OnboardingScaffold(
        step = 1,
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
            onClick = { if (viewModel.validateRemoteUrl()) onNext(state.remoteUrl.trim()) },
            label = "$ next",
        )
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
