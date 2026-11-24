// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zangetsu101.pass.generator.typedPasswordStrength
import com.zangetsu101.pass.passstore.EntryPath
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
internal fun NewEntryScreen(
    initialPath: String,
    viewModel: NewEntryViewModel,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onSaved: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(initialPath) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var customizeOpen by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(true) }
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var replacement by remember { mutableStateOf<EntryReplacement?>(null) }
    val entries by viewModel.entries.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val options = remember(entries, folders) { EntryPathOptions(entries.map { it.path.removeSuffix(".gpg") }, folders) }
    val path = remember(input) { EntryPath(input) }
    val check =
        key(path.value, entries, viewModel.error) {
            val result by produceState<InsertPathCheck?>(null) {
                if (path.error == null) value = viewModel.checkPath(path)
            }
            result
        }
    val exists =
        when (check) {
            is InsertPathCheck.Existing -> true
            InsertPathCheck.Available -> false
            else -> entries.any { it.path == path.filePath }
        }
    val error = path.error?.takeIf { path.value.isNotEmpty() } ?: (check as? InsertPathCheck.Unavailable)?.message
    val enabled = !viewModel.saving && viewModel.savedPath == null
    val canSave = enabled && check != null && error == null && viewModel.password.isNotEmpty()
    val noteLines = if (viewModel.notes.isEmpty()) 0 else viewModel.notes.lineSequence().count()
    val suggestions = remember(options, path) { options.suggestions(path) }

    val changed = input != initialPath || viewModel.passwordTouched || viewModel.notes.isNotEmpty()

    fun leave(
        confirmDiscard: Boolean,
        action: () -> Unit,
    ) {
        if (!enabled) return
        if (confirmDiscard) discardAction = action else action()
    }

    val back = { leave(changed, onBack) }
    BackHandler(onBack = back)
    LaunchedEffect(viewModel.savedPath) { viewModel.savedPath?.let(onSaved) }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            EditorTopBar(title = "new entry", enabled = enabled, onBack = back)
            HorizontalDivider(color = PassColorsDark.Border)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                EntryPathField(
                    input = input,
                    hint = options.hint(path),
                    error = error,
                    exists = exists,
                    enabled = enabled,
                    suggestions = suggestions,
                    onPick = { suggestion ->
                        if (suggestion.folder) {
                            input = "${suggestion.path}/"
                            viewModel.clearError()
                        } else {
                            leave(edited || changed) { onOpenEntry("${suggestion.path}.gpg") }
                        }
                    },
                    onChange = {
                        edited = true
                        input = it
                        viewModel.clearError()
                    },
                )
                Spacer(Modifier.height(20.dp))
                PasswordCard(
                    password = viewModel.password,
                    status = if (viewModel.typedPassword) PasswordStatus.Typed else PasswordStatus.Generated,
                    strength =
                        if (viewModel.typedPassword) {
                            typedPasswordStrength(viewModel.password)
                        } else {
                            viewModel.generator.strength()
                        },
                    enabled = enabled,
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    onPasswordChange = {
                        edited = true
                        viewModel.updatePassword(it)
                    },
                    onRegenerate = viewModel::regenerate,
                    onCopy = viewModel::copyPassword,
                    glow = true,
                )
                Spacer(Modifier.height(8.dp))
                CustomizePanel(
                    settings = viewModel.generator,
                    open = customizeOpen,
                    enabled = enabled,
                    onToggleOpen = { customizeOpen = !customizeOpen },
                    onChange = viewModel::updateGenerator,
                )
                Spacer(Modifier.height(8.dp))
                CopyAfterSaveRow(on = viewModel.copyAfterSave, enabled = enabled, onToggle = viewModel::toggleCopyAfterSave)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = PassColorsDark.Border)
                Spacer(Modifier.height(12.dp))
                NotesToggle(
                    open = notesOpen,
                    lineCount = noteLines,
                    enabled = enabled,
                    onToggle = { notesOpen = !notesOpen },
                )
                if (notesOpen) {
                    Spacer(Modifier.height(6.dp))
                    NotesField(
                        notes = viewModel.notes,
                        enabled = enabled,
                        onNotesChange = {
                            edited = true
                            viewModel.updateNotes(it)
                        },
                    )
                }
            }
            SaveFooter(
                command =
                    if (viewModel.typedPassword) {
                        PassCommand.insert(path.value, multiline = noteLines > 0, force = exists)
                    } else {
                        PassCommand.generate(path.value, viewModel.generator, viewModel.copyAfterSave, force = exists)
                    },
                lineCount = noteLines,
                enabled = canSave,
                saving = viewModel.saving,
                error = viewModel.error,
                showGlow = true,
                onSave = {
                    val digest = (check as? InsertPathCheck.Existing)?.digest
                    if (digest != null) {
                        replacement = EntryReplacement(path, digest)
                    } else {
                        viewModel.save(path, expectedDigest = null)
                    }
                },
            )
        }
    }

    discardAction?.let { action ->
        DiscardChangesDialog(
            title = "discard changes?",
            onDiscard = {
                discardAction = null
                action()
            },
            onKeepEditing = { discardAction = null },
        )
    }
    replacement?.let { request ->
        AlertDialog(
            onDismissRequest = { replacement = null },
            title = { Text("replace ${request.path.value}?") },
            text = { Text("its notes and otp lines will be lost") },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        replacement = null
                        viewModel.save(request.path, request.digest)
                    },
                ) { Text("replace") }
            },
            dismissButton = { TextButton(onClick = { replacement = null }) { Text("cancel") } },
        )
    }
}

private class EntryReplacement(
    val path: EntryPath,
    val digest: ByteArray,
)

@Composable
private fun NotesToggle(
    open: Boolean,
    lineCount: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Text(
        buildAnnotatedString {
            append(if (open) "− " else "+ ")
            append(if (open || lineCount > 0) "notes" else "notes / username")
            if (lineCount > 0) {
                withStyle(SpanStyle(color = PassColorsDark.Accent)) {
                    append(" · $lineCount line")
                    if (lineCount > 1) append("s")
                }
            }
        },
        style =
            PassType.Caption.copy(
                color = if (open || lineCount > 0) PassColorsDark.TextPrimary else PassColorsDark.TextDim,
                fontSize = 10.sp,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
                .padding(vertical = 8.dp),
    )
}

@Composable
private fun EntryPathField(
    input: String,
    hint: String,
    error: String?,
    exists: Boolean,
    enabled: Boolean,
    suggestions: List<PathSuggestion>,
    onPick: (PathSuggestion) -> Unit,
    onChange: (String) -> Unit,
) {
    val color =
        when {
            error != null -> PassColorsDark.Danger
            exists -> PassColorsDark.Warning
            else -> PassColorsDark.Border2
        }
    Text("store path", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PassShapes.small)
            .border(1.dp, color, PassShapes.small),
    ) {
        BasicTextField(
            value = input,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            textStyle = PassType.Body.copy(fontSize = 11.sp),
            cursorBrush = SolidColor(PassColorsDark.Accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .semantics { contentDescription = "entry path" },
            decorationBox = { field ->
                Box {
                    if (input.isEmpty()) {
                        Text(
                            "github.com  or  dev/github",
                            style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
                        )
                    }
                    field()
                }
            },
        )
        if (suggestions.isNotEmpty()) {
            HorizontalDivider(color = color)
            PathSuggestions(suggestions, enabled, onPick)
        }
    }
    val message =
        when {
            error != null -> error
            exists -> "already exists · saving overwrites it (-f)"
            suggestions.isEmpty() -> hint
            else -> null
        }
    if (message != null) {
        Text(
            text = message,
            style = PassType.Caption.copy(fontSize = 9.sp),
            color = if (error != null || exists) color else PassColorsDark.TextDim,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PathSuggestions(
    suggestions: List<PathSuggestion>,
    enabled: Boolean,
    onPick: (PathSuggestion) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(PassColorsDark.Raised)) {
        suggestions.take(3).forEachIndexed { index, suggestion ->
            if (index > 0) HorizontalDivider(color = PassColorsDark.Border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = { onPick(suggestion) })
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append(suggestion.path.substring(0, suggestion.match.first))
                        withStyle(SpanStyle(color = PassColorsDark.Accent)) {
                            append(suggestion.path.substring(suggestion.match))
                        }
                        append(suggestion.path.substring(suggestion.match.last + 1))
                        if (suggestion.folder) append("/")
                    },
                    style = PassType.Body.copy(fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val caption = PassType.Caption.copy(fontSize = 9.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(if (suggestion.folder) "folder" else "exists", style = caption)
                    if (suggestion.folder) FolderCompletionIcon()
                }
            }
        }
        if (suggestions.size > 3) {
            HorizontalDivider(color = PassColorsDark.Border)
            Text(
                "+${suggestions.size - 3} more · keep typing",
                style = PassType.Caption.copy(fontSize = 9.sp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun FolderCompletionIcon() {
    Canvas(Modifier.size(width = 10.dp, height = 9.dp)) {
        val width = size.width
        val height = size.height
        val color = PassColorsDark.TextDim
        val stroke = 1.dp.toPx()
        val arrow =
            Path().apply {
                moveTo(width * 0.2f, height * 0.3f)
                lineTo(width * 0.7f, height * 0.3f)
                moveTo(width * 0.5f, height * 0.1f)
                lineTo(width * 0.7f, height * 0.3f)
                lineTo(width * 0.5f, height * 0.5f)
                moveTo(width * 0.8f, height * 0.7f)
                lineTo(width * 0.3f, height * 0.7f)
                moveTo(width * 0.5f, height * 0.5f)
                lineTo(width * 0.3f, height * 0.7f)
                lineTo(width * 0.5f, height * 0.9f)
            }
        drawPath(arrow, color, style = Stroke(stroke))
        drawLine(color, Offset(width * 0.9f, 0f), Offset(width * 0.9f, height * 0.5f), stroke)
        drawLine(color, Offset(width * 0.1f, height * 0.5f), Offset(width * 0.1f, height), stroke)
    }
}
