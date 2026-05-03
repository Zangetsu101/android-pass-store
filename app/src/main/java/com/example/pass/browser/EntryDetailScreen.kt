package com.example.pass.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pass.passstore.PassEntry
import com.example.pass.ui.components.PassScaffold
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Top bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "back",
                        tint = PassColorsDark.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    if (entry != null) {
                        Text(entry.username, style = PassType.Title)
                        entry.domain?.let { Text(it, style = PassType.Caption.copy(color = PassColorsDark.TextDim)) }
                    }
                }
            }

            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                        Text("PASSWORD", style = PassType.Label)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = String(creds.password),
                            style = PassType.Body.copy(color = PassColorsDark.Accent),
                            modifier = if (!state.passwordRevealed) Modifier.blur(5.dp) else Modifier,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.toggleReveal() },
                                shape = PassShapes.extraSmall,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColorsDark.TextDim),
                                border = BorderStroke(1.dp, PassColorsDark.Border2),
                                modifier = Modifier.weight(1f).height(40.dp),
                            ) {
                                Text(
                                    if (state.passwordRevealed) "$ hide" else "$ reveal",
                                    style = PassType.Body.copy(color = PassColorsDark.TextDim),
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.copyPassword() },
                                shape = PassShapes.extraSmall,
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (state.clipboardCopied) PassColorsDark.Accent else PassColorsDark.TextDim,
                                    ),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        if (state.clipboardCopied) PassColorsDark.Accent else PassColorsDark.Border2,
                                    ),
                                modifier = Modifier.weight(1f).height(40.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (state.clipboardCopied) "copied · 45s" else "copy",
                                    style = PassType.Body,
                                )
                            }
                        }

                        if (creds.notes.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Text("NOTES", style = PassType.Label)
                            Spacer(Modifier.height(6.dp))
                            Text(creds.notes, style = PassType.Body)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Metadata table
                Text("METADATA", style = PassType.Label)
                Spacer(Modifier.height(8.dp))
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
                .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
        Text(value, style = PassType.Caption, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DecryptingShimmer() {
    Column {
        Text("PASSWORD", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        ShimmerBlock(width = 160.dp, height = 16.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBlock(width = 100.dp, height = 40.dp, modifier = Modifier.weight(1f))
            ShimmerBlock(width = 100.dp, height = 40.dp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("NOTES", style = PassType.Label)
        Spacer(Modifier.height(6.dp))
        ShimmerBlock(width = 240.dp, height = 14.dp)
        Spacer(Modifier.height(4.dp))
        ShimmerBlock(width = 180.dp, height = 14.dp)
    }
}

@Composable
private fun ShimmerBlock(
    width: Dp,
    height: Dp,
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
                .width(width)
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
