package com.example.pass.browser

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pass.R
import com.example.pass.passstore.PassEntry
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.components.PassTextField
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassShapes
import com.example.pass.ui.theme.PassType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun EntryDetailScreen(
    entryPath: String,
    viewModel: EntryDetailViewModel,
    onNavigateToSessionStart: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    val entry = remember(entryPath) { viewModel.initForEntry(entryPath) }

    LaunchedEffect(Unit) {
        if (entry != null) {
            viewModel.decrypt(entry, activity)
            viewModel.loadMetadata(entryPath)
        }
    }

    LaunchedEffect(state.sessionStartNeeded) {
        if (state.sessionStartNeeded) {
            onNavigateToSessionStart()
            viewModel.onSessionStartNavigated()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME &&
                    entry != null &&
                    state.credentials == null &&
                    !state.decrypting &&
                    state.decryptError == null
                ) {
                    viewModel.decrypt(entry, activity)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "back",
                            tint = PassColorsDark.TextDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (entry != null) {
                            entry.domain?.let {
                                Text(it, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                            }
                            Text(entry.username, style = PassType.Title)
                        }
                    }
                }

                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    if (entry == null) {
                        Text("Entry not found.", style = PassType.Body, color = PassColorsDark.Danger)
                        return@Column
                    }

                    when {
                        state.decryptError != null -> {
                            Text("error: ${state.decryptError}", color = PassColorsDark.Danger, style = PassType.Body)
                        }

                        state.decrypting || state.credentials == null -> {
                            DecryptingShimmer()
                        }

                        else -> {
                            val creds = checkNotNull(state.credentials)

                            // Password section
                            Text("password", style = PassType.Label)
                            Spacer(Modifier.height(6.dp))
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(PassColorsDark.Surface, RoundedCornerShape(4.dp))
                                        .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = String(creds.password),
                                    style =
                                        PassType.Body.copy(
                                            color = PassColorsDark.Accent,
                                            letterSpacing = 0.1.em,
                                        ),
                                    modifier = if (!state.passwordRevealed) Modifier.blur(5.dp) else Modifier,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.copyPassword() },
                                        shape = PassShapes.extraSmall,
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor = PassColorsDark.AccentDim,
                                                contentColor = PassColorsDark.Accent,
                                            ),
                                        border = BorderStroke(1.dp, PassColorsDark.AccentMid),
                                        modifier = Modifier.weight(1f).height(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (state.clipboardCopied) "copied" else "copy",
                                            style = PassType.Body,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.toggleReveal() },
                                        shape = PassShapes.extraSmall,
                                        colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor = PassColorsDark.Surface,
                                                contentColor = PassColorsDark.TextDim,
                                            ),
                                        border = BorderStroke(1.dp, PassColorsDark.Border2),
                                        modifier = Modifier.weight(1f).height(40.dp),
                                    ) {
                                        Text(
                                            if (state.passwordRevealed) "hide" else "reveal",
                                            style = PassType.Body.copy(color = PassColorsDark.TextDim),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "decrypted in-memory · auto-clears in 45s",
                                style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
                            )

                            Spacer(Modifier.height(20.dp))

                            // Notes section
                            Text("notes", style = PassType.Label)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(PassColorsDark.Surface, RoundedCornerShape(4.dp))
                                        .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp))
                                        .heightIn(min = 60.dp)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    creds.notes.ifEmpty { "no notes" },
                                    style =
                                        PassType.Body.copy(
                                            color = if (creds.notes.isEmpty()) PassColorsDark.TextFaint else PassColorsDark.TextPrimary,
                                        ),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Metadata — always shown
                    Text("metadata", style = PassType.Label)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(PassColorsDark.Surface)
                                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp)),
                    ) {
                        MetaRow("path", entry.path)
                        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                        if (!state.metadataLoaded) {
                            MetaRow("modified", "…")
                            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                            MetaRow("commit", "…")
                        } else {
                            val info = state.commitInfo
                            MetaRow("modified", if (info != null) DATE_FMT.format(info.commitTime) else "unknown")
                            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                            MetaRow("commit", info?.commitHash ?: "unknown")
                        }
                    }
                }
            }

            if (state.biometricNotEnrolled) {
                NoBiometricSheet(
                    state = state,
                    entry = entry,
                    activity = activity,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun NoBiometricSheet(
    state: EntryDetailUiState,
    entry: PassEntry?,
    activity: FragmentActivity,
    viewModel: EntryDetailViewModel,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Color.Black
                            .copy(alpha = 0.55f),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .background(PassColorsDark.Border2, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(PassColorsDark.AccentDim, RoundedCornerShape(8.dp))
                        .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = PassColorsDark.Accent,
                    modifier =
                        Modifier.size(48.dp).graphicsLayer {
                            scaleX = 1.6f
                            scaleY = 1.6f
                        },
                )
            }
            Spacer(Modifier.height(20.dp))

            if (!state.showPassphraseInput) {
                Text("start local session", style = PassType.Title, color = PassColorsDark.Accent)
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "no biometric auth enrolled · passphrase will be cached for 5 min",
                        style = PassType.Caption,
                        color = PassColorsDark.TextFaint,
                    )
                }
                Spacer(Modifier.height(20.dp))
                PassPrimaryButton(
                    onClick = { viewModel.showPassphraseInput() },
                    label = "use passphrase",
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text =
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("enroll a fingerprint in ")
                            val tag = "settings"
                            pushLink(
                                androidx.compose.ui.text.LinkAnnotation.Clickable(tag) {
                                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                },
                            )
                            pushStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    color = PassColorsDark.AccentMid,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                ),
                            )
                            append("Android Settings →")
                            pop()
                            pop()
                        },
                    style =
                        PassType.Caption.copy(
                            color = PassColorsDark.TextFaint,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                )
            } else {
                Text("unlock session", style = PassType.Title, color = PassColorsDark.Accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    "passphrase valid for 5 minutes",
                    style = PassType.Caption,
                    color = PassColorsDark.TextFaint,
                )
                Spacer(Modifier.height(20.dp))
                PassTextField(
                    value = state.passphraseInput,
                    onValueChange = viewModel::setPassphraseInput,
                    placeholder = "passphrase",
                    visualTransformation =
                        androidx.compose.ui.text.input
                            .PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.passphraseError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(state.passphraseError, style = PassType.Caption, color = PassColorsDark.Danger)
                }
                Spacer(Modifier.height(12.dp))
                PassPrimaryButton(
                    onClick = { if (entry != null) viewModel.submitPassphrase(entry, activity) },
                    label = "unlock",
                )
            }
        }
    }
}

@Composable
private fun MetaRow(
    key: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = PassType.Caption.copy(color = PassColorsDark.TextDim), modifier = Modifier.width(72.dp))
        Text(value, style = PassType.Caption, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DecryptingShimmer() {
    // Password card with spinner
    Text("PASSWORD", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = PassColorsDark.Accent,
                trackColor = PassColorsDark.Border2,
                strokeWidth = 2.dp,
            )
            Text("decrypting…", style = PassType.Body.copy(color = PassColorsDark.TextDim))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
            ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "decrypted in-memory · auto-clears in 45s",
        style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
    )

    Spacer(Modifier.height(20.dp))

    // Notes card with shimmer
    Text("NOTES", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp))
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerBlock(height = 10.dp, modifier = Modifier.fillMaxWidth(0.85f))
        ShimmerBlock(height = 10.dp, modifier = Modifier.fillMaxWidth(0.60f))
    }
}

@Composable
private fun ShimmerBlock(
    height: Dp,
    width: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offsetX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    Box(
        modifier =
            modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .height(height)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                PassColorsDark.Surface,
                                PassColorsDark.Raised,
                                PassColorsDark.Surface,
                            ),
                        start = Offset(offsetX, 0f),
                        end = Offset(offsetX + 300f, 0f),
                    ),
                    RoundedCornerShape(3.dp),
                ),
    )
}
