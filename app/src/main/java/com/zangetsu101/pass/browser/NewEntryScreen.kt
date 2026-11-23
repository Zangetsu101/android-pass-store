// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(true) }
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var replaceDigest by remember { mutableStateOf<ByteArray?>(null) }
    val entries by viewModel.entries.collectAsState()
    val options = remember(entries) { EntryPathOptions(entries.map { it.path.removeSuffix(".gpg") }) }
    val path = remember(input) { EntryPath(input) }
    val check =
        key(path.value, entries, viewModel.error) {
            val result by produceState<InsertPathCheck?>(null) {
                if (path.error == null) value = viewModel.checkPath(path)
            }
            result
        }
    val exists =
        if (check != null && check.error == null) check.digest != null else entries.any { it.path == path.filePath }
    val error = path.error?.takeIf { path.value.isNotEmpty() } ?: check?.error
    val enabled = !viewModel.saving && viewModel.savedPath == null
    val canSave = enabled && check != null && error == null && viewModel.password.isNotEmpty()
    val noteLines = if (viewModel.notes.isEmpty()) 0 else viewModel.notes.lines().size
    val suggestions = remember(options, path) { options.suggestions(path) }

    fun leave(action: () -> Unit) {
        if (!enabled) return
        val changed = input != initialPath || viewModel.password.isNotEmpty() || noteLines > 0
        if (changed) discardAction = action else action()
    }

    val back = { leave(onBack) }
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
                    hint = options.hint(path, folderExists = check?.folderExists == true),
                    error = error,
                    exists = exists,
                    enabled = enabled,
                    onChange = {
                        input = it
                        viewModel.clearError()
                    },
                )
                PathSuggestions(suggestions, enabled) { suggestion ->
                    if (suggestion.folder) {
                        input = "${suggestion.path}/"
                        viewModel.clearError()
                    } else {
                        leave { onOpenEntry("${suggestion.path}.gpg") }
                    }
                }
                Spacer(Modifier.height(20.dp))
                PasswordEditor(
                    password = viewModel.password,
                    enabled = enabled,
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    onPasswordChange = viewModel::updatePassword,
                    onCopy = viewModel::copyPassword,
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = PassColorsDark.Border)
                Spacer(Modifier.height(12.dp))
                if (notesOpen) {
                    NotesEditor(notes = viewModel.notes, enabled = enabled, onNotesChange = viewModel::updateNotes)
                } else {
                    TextButton(onClick = { notesOpen = true }, enabled = enabled) {
                        Text("+ notes / username", style = PassType.Caption.copy(color = PassColorsDark.Accent))
                    }
                }
            }
            SaveFooter(
                path = path.value,
                flags = (if (noteLines > 0) "-m " else "") + (if (exists) "-f " else ""),
                lineCount = noteLines,
                enabled = canSave,
                saving = viewModel.saving,
                error = viewModel.error,
                onSave = {
                    if (exists) replaceDigest = check?.digest else viewModel.save(path, expectedDigest = null)
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
    replaceDigest?.let { expectedDigest ->
        AlertDialog(
            onDismissRequest = { replaceDigest = null },
            title = { Text("replace ${path.value}?") },
            text = { Text("its notes and otp lines will be lost") },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        replaceDigest = null
                        viewModel.save(path, expectedDigest)
                    },
                ) { Text("replace") }
            },
            dismissButton = { TextButton(onClick = { replaceDigest = null }) { Text("cancel") } },
        )
    }
}

@Composable
private fun EntryPathField(
    input: String,
    hint: String,
    error: String?,
    exists: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val color =
        when {
            error != null -> PassColorsDark.Danger
            exists -> PassColorsDark.Warning
            else -> PassColorsDark.Accent
        }
    Text("path", style = PassType.Label)
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = input,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        textStyle = PassType.Body,
        cursorBrush = SolidColor(PassColorsDark.Accent),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, color, PassShapes.small)
                .padding(12.dp)
                .semantics { contentDescription = "entry path" },
        decorationBox = { field ->
            Box {
                if (input.isEmpty()) Text("folder/name", style = PassType.Body.copy(color = PassColorsDark.TextFaint))
                field()
            }
        },
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = error ?: if (exists) "already exists · saving overwrites it (-f)" else hint,
        style = PassType.Caption,
        color = if (error != null || exists) color else PassColorsDark.TextDim,
    )
}

@Composable
private fun PathSuggestions(
    suggestions: List<PathSuggestion>,
    enabled: Boolean,
    onPick: (PathSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        suggestions.take(3).forEach { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = { onPick(suggestion) })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val end = suggestion.matchStart + suggestion.matchLength
                Text(
                    buildAnnotatedString {
                        append(suggestion.path.substring(0, suggestion.matchStart))
                        withStyle(SpanStyle(color = PassColorsDark.Accent)) {
                            append(suggestion.path.substring(suggestion.matchStart, end))
                        }
                        append(suggestion.path.substring(end))
                        if (suggestion.folder) append("/")
                    },
                    style = PassType.Body,
                    modifier = Modifier.weight(1f),
                )
                Text(if (suggestion.folder) "folder ↹" else "exists", style = PassType.Caption)
            }
        }
        if (suggestions.size > 3) {
            Text(
                "+${suggestions.size - 3} more · keep typing",
                style = PassType.Caption,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
