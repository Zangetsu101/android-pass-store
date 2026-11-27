// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zangetsu101.pass.decryption.noteLineCount
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
internal fun EntryEditorScreen(
    editor: EntryEditorState,
    viewModel: EntryDetailViewModel,
) {
    val path = editor.path
    val type = editor.type
    val noteLines = noteLineCount(editor.notes)
    var revealed by remember { mutableStateOf(false) }
    var customizeOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val back = {
        if (!editor.saving) {
            if (editor.changed) confirmDiscard = true else viewModel.discardEditor()
        }
    }
    BackHandler(onBack = back)

    // The safeDrawing insets include the IME, so the footer stays above the keyboard.
    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            EditorTopBar(title = "edit entry", path = path, enabled = !editor.saving, onBack = back)
            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            ) {
                when (val secret = editor.secretDraft) {
                    is SecretDraft.CardNumber -> {
                        CardNumberField(secret.digits, enabled = !editor.saving, onChange = viewModel::updateEditorCardNumber)
                    }

                    is SecretDraft.Password -> {
                        PasswordCard(
                            password = secret.draft.password,
                            status =
                                when {
                                    secret.draft.generated -> PasswordStatus.Generated
                                    editor.secretChanged -> PasswordStatus.Typed
                                    else -> null
                                },
                            strength = secret.draft.strength,
                            enabled = !editor.saving,
                            revealed = revealed,
                            onToggleReveal = { revealed = !revealed },
                            onPasswordChange = viewModel::updateEditorPassword,
                            onRegenerate = {
                                revealed = true
                                viewModel.regenerateEditorPassword()
                            },
                            onCopy = viewModel::copyEditorPassword,
                        )
                        Spacer(Modifier.height(8.dp))
                        CustomizePanel(
                            settings = secret.draft.settings,
                            open = customizeOpen,
                            enabled = !editor.saving,
                            onToggleOpen = { customizeOpen = !customizeOpen },
                            onChange = {
                                revealed = true
                                viewModel.updateEditorGenerator(it)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                NotesEditor(
                    path = path,
                    notes = editor.notes,
                    lineCount = noteLines,
                    type = type,
                    enabled = !editor.saving,
                    onNotesChange = viewModel::updateEditorNotes,
                )
                if (editor.hasOtp) {
                    Spacer(Modifier.height(16.dp))
                    OtpKeptRow()
                }
            }
            SaveFooter(
                command = editor.plan.command,
                lineSummary = saveLineSummary(noteLines, type),
                enabled = editor.changed && !editor.saving,
                saving = editor.saving,
                error = editor.error,
                onSave = viewModel::saveEditor,
            )
        }
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            title = "discard changes to $path?",
            onDiscard = viewModel::discardEditor,
            onKeepEditing = { confirmDiscard = false },
        )
    }
}

@Composable
internal fun DiscardChangesDialog(
    title: String,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDiscard) { Text("discard") } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text("keep editing") } },
    )
}

@Composable
internal fun EditorTopBar(
    title: String,
    path: String? = null,
    enabled: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "back",
                tint = PassColorsDark.TextDim,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = PassType.Title)
            if (path != null) Text(path, style = PassType.Caption)
        }
        actions()
    }
}

@Composable
internal fun NotesEditor(
    path: String,
    notes: String,
    lineCount: Int,
    type: EntryType,
    enabled: Boolean,
    onNotesChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(if (type == EntryType.Card) "card details" else "notes", style = PassType.Label)
        if (lineCount > 0) {
            Text(
                " · $lineCount line${if (lineCount > 1) "s" else ""}",
                style = PassType.Caption.copy(color = PassColorsDark.Accent),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    NotesField(path, notes, enabled, type, onNotesChange)
}

/** The notes of an entry, with chips that add common `key: ` lines for the entry at [path]. */
@Composable
internal fun NotesField(
    path: String,
    notes: String,
    enabled: Boolean,
    type: EntryType,
    onNotesChange: (String) -> Unit,
) {
    val card = type == EntryType.Card
    var typed by remember { mutableStateOf(TextFieldValue(notes)) }
    val value = typed.syncedWith(notes)
    val focusRequester = remember { FocusRequester() }
    NoteChipRow(
        chips = noteChips(path, notes, type),
        enabled = enabled,
        onAdd = { key ->
            onNotesChange(addNoteKey(notes, key))
            focusRequester.requestFocus()
        },
    )
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        // Line 1 of the file is the secret above, so the notes editor shows it as locked.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .dashedBottomBorder(PassColorsDark.Border2)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "••••••••••",
                style = PassType.Caption.copy(letterSpacing = 0.1.em),
                modifier = Modifier.weight(1f),
            )
            Text("line 1 · ${type.secretLabel}", style = PassType.Caption)
        }
        BasicTextField(
            value = value,
            onValueChange = {
                typed = it
                if (it.text != notes) onNotesChange(it.text)
            },
            enabled = enabled,
            singleLine = false,
            minLines = 3,
            textStyle = PassType.Body,
            cursorBrush = SolidColor(PassColorsDark.Accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics { contentDescription = "edit notes" },
            decorationBox = { innerTextField ->
                Box {
                    if (notes.isEmpty()) {
                        Text(
                            "tap a chip, or write anything" + if (card) "" else "\nrecovery codes, security answers…",
                            style = PassType.Body.copy(color = PassColorsDark.TextFaint),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
    Spacer(Modifier.height(6.dp))
    Text("saved as typed · key: value lines get their own copy row", style = PassType.Caption)
}

/** Returns this value when its text is [text]. When a chip changed the text, returns [text] with the cursor at the end. */
internal fun TextFieldValue.syncedWith(text: String): TextFieldValue {
    if (this.text == text) return this
    return TextFieldValue(text, TextRange(text.length))
}

private class ChipLook(
    val mark: String,
    val text: Color,
    val background: Color,
    val border: Color,
)

private val unusedChip = ChipLook("+ ", PassColorsDark.Accent, PassColorsDark.Surface, PassColorsDark.AccentMid)
private val usedChip = ChipLook("✓ ", PassColorsDark.TextDim, Color.Transparent, PassColorsDark.Border)

@Composable
private fun NoteChipRow(
    chips: List<NoteChip>,
    enabled: Boolean,
    onAdd: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { chip ->
            val look = if (chip.used) usedChip else unusedChip
            Text(
                look.mark + chip.key + ":",
                style = PassType.Caption.copy(color = look.text),
                modifier =
                    Modifier
                        .clip(PassShapes.extraSmall)
                        .background(look.background)
                        .border(1.dp, look.border, PassShapes.extraSmall)
                        .clickable(enabled = enabled && !chip.used, role = Role.Button) { onAdd(chip.key) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun OtpKeptRow() {
    Text(
        "otp · kept as-is",
        style = PassType.Caption,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, PassColorsDark.Border, PassShapes.small)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SaveFooter(
    command: PassCommand,
    lineSummary: String?,
    enabled: Boolean,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
    showGlow: Boolean = false,
) {
    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)) {
        error?.let {
            Text(it, style = PassType.Caption.copy(color = PassColorsDark.Danger))
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (enabled &&
                            showGlow
                        ) {
                            Modifier.dropShadow(PassShapes.small, Shadow(radius = 12.dp, color = PassColorsDark.AccentDim))
                        } else {
                            Modifier
                        },
                    ).background(if (enabled) PassColorsDark.AccentDim else PassColorsDark.Border, PassShapes.small)
                    .border(1.dp, if (enabled) PassColorsDark.Accent else PassColorsDark.Border, PassShapes.small)
                    .clip(PassShapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSave)
                    // Maestro taps the button by this description. The styled text below has no stable string.
                    .semantics(mergeDescendants = true) { contentDescription = "save entry" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (saving) savingText() else commandText(command, enabled),
                    style = PassType.Caption.copy(fontWeight = FontWeight.SemiBold),
                )
                val detail = PassType.Caption.copy(color = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint)
                lineSummary?.let { Text(it, style = detail) }
                command.charset?.let { Text("charset  $it", style = detail) }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "↵",
                style =
                    PassType.Caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint,
                    ),
            )
        }
    }
}

/** The line below the command in the save button. A card counts line 1 too, because a card always has its number. */
internal fun saveLineSummary(
    noteLines: Int,
    type: EntryType,
): String? {
    fun plural(count: Int) = if (count == 1) "" else "s"
    return when {
        type == EntryType.Card -> {
            val details = if (noteLines > 0) " + $noteLines detail${plural(noteLines)}" else ""
            "${noteLines + 1} line${plural(noteLines + 1)} · number$details"
        }

        noteLines > 0 -> {
            "+ $noteLines line${plural(noteLines)} of notes"
        }

        else -> {
            null
        }
    }
}

private fun savingText(): AnnotatedString = AnnotatedString("saving…", SpanStyle(color = PassColorsDark.TextDim))

/** The `pass` command that matches the save, with the flags in the warning color. */
private fun commandText(
    command: PassCommand,
    enabled: Boolean,
): AnnotatedString {
    val prompt = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint
    val text = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint
    val flag = if (enabled) PassColorsDark.Warning else PassColorsDark.TextFaint
    return buildAnnotatedString {
        withStyle(SpanStyle(color = prompt)) { append("> ") }
        command.parts.forEachIndexed { index, part ->
            if (index > 0) append(" ")
            withStyle(SpanStyle(color = if (part.highlighted) flag else text)) { append(part.text) }
        }
    }
}

private fun Modifier.dashedBottomBorder(color: Color): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        val y = size.height - stroke / 2
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
