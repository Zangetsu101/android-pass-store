package com.zangetsu101.pass.browser

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val activity = LocalActivity.current as FragmentActivity

    LaunchedEffect(Unit) {
        viewModel.authenticate(activity)
    }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                EntryTopBar(entry = state.entry, onBack = onBack)
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    when (val unlock = state.unlockState) {
                        is UnlockState.Idle,
                        is UnlockState.Decrypting,
                        -> PasswordSkeleton()

                        is UnlockState.Decrypted -> DecryptedContent(unlock, state.clipboardTimeoutSeconds, viewModel)

                        is UnlockState.Failed -> ErrorMessage(unlock.message)
                    }

                    if (state.unlockState !is UnlockState.Decrypted && state.unlockState !is UnlockState.Failed) {
                        Spacer(Modifier.height(6.dp))
                        // Invisible placeholder — reserves identical height to the real caption in
                        // DecryptedContent so layout doesn't shift on transition.
                        Text(
                            "decrypted in-memory",
                            style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
                            modifier = Modifier.alpha(0f),
                        )
                        Spacer(Modifier.height(20.dp))
                        NotesSkeleton()
                    }

                    Spacer(Modifier.height(16.dp))
                    MetadataCard(
                        entry = state.entry,
                        gitStatus = state.gitStatus,
                    )
                }
            }

            if (state.unlockState is UnlockState.Idle) {
                PassPrimaryButton(
                    onClick = { viewModel.authenticate(activity) },
                    label = "decrypt",
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
                )
            }
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun EntryTopBar(
    entry: PassEntry,
    onBack: () -> Unit,
) {
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
            entry.domain?.let {
                Text(it, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
            }
            Text(entry.username, style = PassType.Title)
        }
    }
}

@Composable
private fun DecryptedContent(
    unlock: UnlockState.Decrypted,
    clipboardTimeoutSeconds: Int,
    viewModel: EntryDetailViewModel,
) {
    PasswordCard(unlock = unlock, onCopy = viewModel::copyPassword, onToggleReveal = viewModel::toggleReveal)
    Spacer(Modifier.height(6.dp))
    Text(
        "decrypted in-memory" + if (unlock.clipboardCopied) " · clipboard clears in ${clipboardTimeoutSeconds}s" else "",
        style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
    )
    Spacer(Modifier.height(20.dp))
    NotesCard(notes = unlock.credentials.notes)
}

@Composable
private fun PasswordCard(
    unlock: UnlockState.Decrypted,
    onCopy: () -> Unit,
    onToggleReveal: () -> Unit,
) {
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
            text = String(unlock.credentials.password),
            style = PassType.Body.copy(color = PassColorsDark.Accent, letterSpacing = 0.1.em),
            modifier = if (!unlock.passwordRevealed) Modifier.blur(5.dp) else Modifier,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onCopy,
                shape = PassShapes.extraSmall,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = PassColorsDark.AccentDim,
                        contentColor = PassColorsDark.Accent,
                    ),
                border = BorderStroke(1.dp, PassColorsDark.AccentMid),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (unlock.clipboardCopied) "copied" else "copy", style = PassType.Body)
            }
            OutlinedButton(
                onClick = onToggleReveal,
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
                    if (unlock.passwordRevealed) "hide" else "reveal",
                    style = PassType.Body.copy(color = PassColorsDark.TextDim),
                )
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
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
            notes.ifEmpty { "no notes" },
            style =
                PassType.Body.copy(
                    color = if (notes.isEmpty()) PassColorsDark.TextFaint else PassColorsDark.TextPrimary,
                ),
        )
    }
}

@Composable
private fun MetadataCard(
    entry: PassEntry,
    gitStatus: GitStatus,
) {
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
        when (gitStatus) {
            is GitStatus.Loading -> {
                MetaRow("modified", "…")
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                MetaRow("commit", "…")
            }

            is GitStatus.Untracked -> {
                MetaRow("modified", "unknown")
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                MetaRow("commit", "untracked")
            }

            is GitStatus.Tracked -> {
                MetaRow("modified", DATE_FMT.format(gitStatus.commitInfo.commitTime))
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                MetaRow("commit", gitStatus.commitInfo.commitHash)
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text("error: $message", color = PassColorsDark.Danger, style = PassType.Body)
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
private fun PasswordSkeleton() {
    val bodyHeight = with(LocalDensity.current) { PassType.Body.fontSize.toDp() }
    Text("password", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerBlock(height = bodyHeight, modifier = Modifier.fillMaxWidth(0.55f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
            ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotesSkeleton() {
    val bodyHeight = with(LocalDensity.current) { PassType.Body.fontSize.toDp() }
    Text("notes", style = PassType.Label)
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
        ShimmerBlock(height = bodyHeight, modifier = Modifier.fillMaxWidth(0.85f))
        ShimmerBlock(height = bodyHeight, modifier = Modifier.fillMaxWidth(0.60f))
    }
}

@Composable
private fun MetadataSkeleton() {
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
        MetaRow("path", "…")
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        MetaRow("modified", "…")
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        MetaRow("commit", "…")
    }
}

@Composable
private fun ShimmerBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    Box(
        modifier =
            modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .height(height)
                .background(PassColorsDark.Raised, RoundedCornerShape(3.dp)),
    )
}
