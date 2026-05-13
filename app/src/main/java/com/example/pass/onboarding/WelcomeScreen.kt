package com.example.pass.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pass.ui.components.PassAppIcon
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onStart: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.recheck()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(top = 40.dp)) {
                PassAppIcon(size = 52.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text =
                        buildAnnotatedString {
                            append("pass")
                            withStyle(SpanStyle(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light)) {
                                append(".android")
                            }
                        },
                    style = PassType.Display,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "the standard unix password manager — on your phone",
                    style = PassType.Body,
                    color = PassColorsDark.TextDim,
                )
                Spacer(Modifier.height(28.dp))
                PreflightPanel(state)
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                val failure = state.primaryFailure
                when {
                    state.isReady -> {
                        PassPrimaryButton(onClick = onStart, label = "> pass init")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "requires git + gpg key",
                            style = PassType.Caption,
                            color = PassColorsDark.TextFaint,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    failure != null -> {
                        BlockedActions(failure, onStart)
                    }

                    else -> {
                        GhostCloneButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun PreflightPanel(state: PreflightUiState) {
    val hasFailure = state.primaryFailure != null && !state.isLoading
    val borderColor =
        if (hasFailure) PassColorsDark.Danger.copy(alpha = 0.33f) else PassColorsDark.Border2

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    PassColorsDark.Surface,
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(3.dp),
                ).then(
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    },
                ).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
            Text("pass --check-device", style = PassType.Caption.copy(color = PassColorsDark.TextPrimary))
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
        CheckRow("biometric", state.biometric)
        CheckRow("android api", state.apiLevel)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        if (!state.isLoading) {
            val failCount = listOf(state.biometric, state.apiLevel).count { it is CheckResult.Fail }
            Text(
                text =
                    if (failCount == 0) {
                        "device ready · 2 of 2 passed"
                    } else {
                        "blocked · $failCount check${if (failCount > 1) "s" else ""} failed"
                    },
                style =
                    PassType.Label.copy(
                        color = if (failCount == 0) PassColorsDark.Accent else PassColorsDark.Danger,
                    ),
            )
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    result: CheckResult,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.Center) {
            when (result) {
                is CheckResult.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = PassColorsDark.Accent,
                        strokeWidth = 1.5.dp,
                    )
                }

                is CheckResult.Pass -> {
                    Text(
                        "✓",
                        style = PassType.Caption.copy(color = PassColorsDark.Accent, fontWeight = FontWeight.Bold),
                    )
                }

                is CheckResult.Fail -> {
                    Text(
                        "✗",
                        style = PassType.Caption.copy(color = PassColorsDark.Danger, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
        Text(label, style = PassType.Caption.copy(color = PassColorsDark.TextDim), modifier = Modifier.weight(1f))
        if (result !is CheckResult.Loading) {
            val value =
                when (result) {
                    is CheckResult.Pass -> result.value
                    is CheckResult.Fail -> result.value
                }
            Text(
                value,
                style =
                    PassType.Caption.copy(
                        color = if (result is CheckResult.Pass) PassColorsDark.Accent else PassColorsDark.Danger,
                    ),
            )
        }
    }
}

@Composable
private fun BlockedActions(
    failure: PreflightFailure,
    onStart: () -> Unit,
) {
    val context = LocalContext.current

    val (title, description) =
        when (failure) {
            is PreflightFailure.BiometricNotEnrolled -> {
                if (failure.canUseDeviceCredential) {
                    "add a fingerprint, face, or screen lock" to
                        "pass.android locks your store to your biometric or screen lock. enroll a fingerprint, face, or PIN in Android Settings and we'll re-check automatically."
                } else {
                    "add a fingerprint or face" to
                        "pass.android locks your store to your biometric signature. enroll a fingerprint or face in Android Settings and we'll re-check automatically."
                }
            }

            PreflightFailure.ApiTooLow -> {
                "android 11 or newer required" to
                    "pass.android relies on platform biometric apis introduced in android 11 (api 30). update your system or check with your device manufacturer for an upgrade."
            }
        }

    Text(title, style = PassType.Body.copy(fontWeight = FontWeight.SemiBold))
    Spacer(Modifier.height(6.dp))
    Text(description, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
    Spacer(Modifier.height(14.dp))

    if (failure is PreflightFailure.BiometricNotEnrolled) {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        if (failure.canUseDeviceCredential) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG,
                    )
                }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
        PassPrimaryButton(
            onClick = { context.startActivity(intent) },
            label = "open android settings →",
        )
        Spacer(Modifier.height(8.dp))
    }

    GhostCloneButton()
    Spacer(Modifier.height(10.dp))
    Text(
        text = "re-checks automatically when you return",
        style = PassType.Caption.copy(color = PassColorsDark.TextFaint, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GhostCloneButton() {
    val borderColor = PassColorsDark.Border2
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style =
                            Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                            ),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text("> pass init", style = PassType.Body.copy(color = PassColorsDark.TextFaint))
    }
}
