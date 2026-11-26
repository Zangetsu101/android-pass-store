// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zangetsu101.pass.decryption.NoteField
import com.zangetsu101.pass.otp.Otp
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.components.PassSecondaryButton
import com.zangetsu101.pass.ui.components.PassTag
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
    sessionActive: Boolean,
    onSessionRequired: () -> Unit,
) {
    val state = viewModel.state.collectAsState().value
    val activity = LocalActivity.current as FragmentActivity

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(viewModel, lifecycleOwner) { mutableStateOf(false) }
    val leaveEntry = {
        viewModel.lock()
        onBack()
    }

    DisposableEffect(viewModel, lifecycleOwner, activity) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshLock()
                    resumed = true
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    resumed = false
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenDisposed(changingConfigurations = activity.isChangingConfigurations)
        }
    }

    val mode = state.mode
    LaunchedEffect(viewModel, activity, sessionActive, mode is EntryDetailMode.ViewEntry, state.showingSavedEntry) {
        if (sessionActive) {
            viewModel.onScreenEntered(activity)
        } else if (mode is EntryDetailMode.ViewEntry && !state.showingSavedEntry) {
            onSessionRequired()
        }
    }

    if (mode is EntryDetailMode.EditEntry) {
        EntryEditorScreen(state.entry, mode.draft, viewModel)
        return
    }
    BackHandler(onBack = leaveEntry)

    // A collected decrypted snapshot may outlive the synchronous resume lock check.
    val unlockState =
        if (!resumed || (state.unlockState is UnlockState.Decrypted && !viewModel.hasUnexpiredDetails())) {
            UnlockState.Idle
        } else {
            state.unlockState
        }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                EntryTopBar(
                    label = headerLabel(state.entry),
                    name = unlockState.headerName(state.entry),
                    isCard = state.entry.isCard,
                    onBack = leaveEntry,
                )
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    when (val unlock = unlockState) {
                        is UnlockState.Idle,
                        is UnlockState.Decrypting,
                        -> SecretSkeleton(state.entry.type.secretLabel)

                        is UnlockState.Decrypted -> DecryptedContent(unlock, state.entry, state.clipboardTimeoutSeconds, viewModel)

                        is UnlockState.Failed -> ErrorMessage(unlock.message)
                    }

                    if (unlockState !is UnlockState.Decrypted && unlockState !is UnlockState.Failed) {
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
                    if (sessionActive && unlockState is UnlockState.Decrypted && unlockState.canEdit) {
                        Spacer(Modifier.height(16.dp))
                        PassSecondaryButton(onClick = viewModel::startEditing, label = "edit")
                    }
                }
            }

            if (resumed && unlockState is UnlockState.Idle) {
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

/** The line above the name. A card shows its path, because the domain of a card is only a display label. */
private fun headerLabel(entry: PassEntry) = if (entry.isCard) entry.path.removeSuffix(".gpg") else entry.domain

@Composable
private fun EntryTopBar(
    label: String?,
    name: String,
    isCard: Boolean,
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
        Column(modifier = Modifier.weight(1f)) {
            label?.let {
                Text(it, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
            }
            Text(name, style = PassType.Title)
        }
        if (isCard) PassTag("card")
    }
}

@Composable
private fun DecryptedContent(
    unlock: UnlockState.Decrypted,
    entry: PassEntry,
    clipboardTimeoutSeconds: Int,
    viewModel: EntryDetailViewModel,
) {
    if (unlock.credentials.secret.isNotEmpty()) {
        val secret = String(unlock.credentials.secret)
        SecretCard(
            label = entry.type.secretLabel,
            text = if (entry.isCard && isCardDigits(secret)) groupCardNumber(secret) else secret,
            unlock = unlock,
            onCopy = viewModel::copySecret,
            onToggleReveal = viewModel::toggleReveal,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "decrypted in-memory" + if (unlock.copied == Copied.Secret) " · clipboard clears in ${clipboardTimeoutSeconds}s" else "",
            style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
        )
        Spacer(Modifier.height(20.dp))
    }
    unlock.credentials.otp?.let { otp ->
        OtpCard(otp, unlock.copied == Copied.OtpCode, clipboardTimeoutSeconds, viewModel::copyOtp)
        Spacer(Modifier.height(20.dp))
    }
    val notes = unlock.notes
    val otherLines =
        notes.lines
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    if (notes.fields.isNotEmpty()) {
        FieldsCard(
            fields = notes.fields,
            revealed = unlock.revealedFields,
            copied = (unlock.copied as? Copied.Field)?.index,
            onToggleReveal = viewModel::toggleFieldReveal,
            onCopy = viewModel::copyField,
        )
        if (otherLines.isNotEmpty()) Spacer(Modifier.height(20.dp))
    }
    if (otherLines.isNotEmpty() || notes.fields.isEmpty()) NotesCard(notes = otherLines)
}

/** The Note Fields, each with a copy button. A secret value stays blurred until the user taps it. */
@Composable
private fun FieldsCard(
    fields: List<NoteField>,
    revealed: Set<Int>,
    copied: Int?,
    onToggleReveal: (Int) -> Unit,
    onCopy: (Int) -> Unit,
) {
    Text("details", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PassShapes.small)
                .background(PassColorsDark.Surface)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        fields.forEachIndexed { index, field ->
            if (index > 0) HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            FieldRow(
                field = field,
                revealed = index in revealed,
                copied = copied == index,
                onToggleReveal = { onToggleReveal(index) },
                onCopy = { onCopy(index) },
            )
        }
    }
}

@Composable
private fun FieldRow(
    field: NoteField,
    revealed: Boolean,
    copied: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    val valueStyle = PassType.Caption.copy(color = PassColorsDark.TextPrimary)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            field.key,
            style = PassType.Caption.copy(color = PassColorsDark.TextDim),
            modifier = Modifier.widthIn(min = 72.dp).padding(end = 8.dp),
        )
        if (field.secret) {
            Text(
                field.value,
                style = valueStyle.copy(letterSpacing = 0.08.em),
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onToggleReveal)
                        .semantics { contentDescription = "${if (revealed) "hide" else "reveal"} ${field.key}" }
                        .padding(vertical = 8.dp)
                        .then(if (revealed) Modifier else Modifier.blur(5.dp)),
            )
        } else {
            SelectionContainer(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(field.value, style = valueStyle)
            }
        }
        Box(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(PassShapes.extraSmall)
                    .clickable(role = Role.Button, onClick = onCopy)
                    .semantics { contentDescription = "copy ${field.key}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (copied) "copied" else "copy",
                style = PassType.Caption.copy(color = PassColorsDark.Accent),
                modifier =
                    Modifier
                        .border(1.dp, PassColorsDark.AccentMid, PassShapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun OtpCard(
    otp: Otp,
    copied: Boolean,
    clipboardTimeoutSeconds: Int,
    onCopy: () -> Unit,
) {
    var now by remember(otp) { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(otp) {
        if (otp is Otp.Totp) {
            while (true) {
                now = System.currentTimeMillis() / 1000
                delay(200)
            }
        }
    }
    Text("otp", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .clip(PassShapes.small)
                .then(if (otp is Otp.Totp) Modifier.clickable(onClick = onCopy) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (otp) {
            is Otp.Invalid -> {
                Text("error: ${otp.reason}", style = PassType.Body, color = PassColorsDark.Danger)
            }

            is Otp.Totp -> {
                val left = otp.secondsRemaining(now)
                val color = if (left <= 5) PassColorsDark.Warning else PassColorsDark.Accent
                val code = otp.code(now)
                Column(Modifier.weight(1f)) {
                    Text(
                        code.chunked((code.length + 1) / 2).joinToString(" "),
                        style = PassType.Title.copy(fontSize = 26.sp, letterSpacing = 0.08.em, color = color),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (copied) "copied" else "tap to copy",
                        style = PassType.Caption.copy(color = if (copied) PassColorsDark.Accent else PassColorsDark.TextDim),
                    )
                }
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = {
                        left.toFloat() / otp.period
                    }, modifier = Modifier.size(34.dp), color = color, strokeWidth = 3.dp, trackColor = PassColorsDark.Border2)
                    Text(left.toString(), style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        when (otp) {
            is Otp.Invalid -> "no code available"
            is Otp.Totp -> if (copied) "clipboard clears in ${clipboardTimeoutSeconds}s" else "totp · ${otp.digits} digits · ${otp.period}s"
        },
        style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
    )
}

@Composable
private fun SecretCard(
    label: String,
    text: String,
    unlock: UnlockState.Decrypted,
    onCopy: () -> Unit,
    onToggleReveal: () -> Unit,
) {
    Text(label, style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = PassType.Body.copy(color = PassColorsDark.Accent, letterSpacing = 0.1.em),
            modifier = if (!unlock.secretRevealed) Modifier.blur(5.dp) else Modifier,
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
                Text(if (unlock.copied == Copied.Secret) "copied" else "copy", style = PassType.Body)
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
                    if (unlock.secretRevealed) "hide" else "reveal",
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
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        SelectionContainer {
            Text(
                notes.ifEmpty { "no notes" },
                style =
                    PassType.Body.copy(
                        color = if (notes.isEmpty()) PassColorsDark.TextFaint else PassColorsDark.TextPrimary,
                    ),
            )
        }
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
                .clip(PassShapes.small)
                .background(PassColorsDark.Surface)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        MetaRow("type", if (entry.isCard) "card" else "login")
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
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
private fun SecretSkeleton(label: String) {
    val bodyHeight = with(LocalDensity.current) { PassType.Body.fontSize.toDp() }
    Text(label, style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
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
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
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
                .clip(PassShapes.small)
                .background(PassColorsDark.Surface)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
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
                .background(PassColorsDark.Raised, PassShapes.extraSmall),
    )
}
