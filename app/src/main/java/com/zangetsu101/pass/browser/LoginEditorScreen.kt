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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
internal fun LoginEditorScreen(
    entry: PassEntry,
    editor: LoginEditorState,
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
    confirmDiscard: Boolean,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    val path = entry.path.removeSuffix(".gpg")
    var revealed by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    // The safeDrawing insets include the IME, so the footer stays above the keyboard.
    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            EditorTopBar(path = path, enabled = !editor.saving, onBack = onBack)
            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            ) {
                PasswordEditor(
                    editor = editor,
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    onPasswordChange = viewModel::updateEditorPassword,
                    onCopy = viewModel::copyEditorPassword,
                )
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                NotesEditor(editor = editor, onNotesChange = viewModel::updateEditorNotes)
                if (editor.hasOtp) {
                    Spacer(Modifier.height(16.dp))
                    OtpKeptRow()
                }
            }
            SaveFooter(path = path, editor = editor, onSave = viewModel::saveEditor)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = onKeepEditing,
            title = { Text("discard changes to $path?") },
            confirmButton = { TextButton(onClick = onDiscard) { Text("discard") } },
            dismissButton = { TextButton(onClick = onKeepEditing) { Text("keep editing") } },
        )
    }
}

@Composable
private fun EditorTopBar(
    path: String,
    enabled: Boolean,
    onBack: () -> Unit,
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
            Text("edit entry", style = PassType.Title)
            Text(path, style = PassType.Caption)
        }
    }
}

@Composable
private fun PasswordEditor(
    editor: LoginEditorState,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onCopy: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("password", style = PassType.Label, modifier = Modifier.weight(1f))
        if (editor.passwordChanged) {
            Text("edited by you", style = PassType.Caption.copy(color = PassColorsDark.Accent))
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Accent.copy(alpha = 0.33f), PassShapes.small)
                .clip(PassShapes.small),
    ) {
        BasicTextField(
            value = editor.password,
            onValueChange = onPasswordChange,
            enabled = !editor.saving,
            singleLine = false,
            textStyle =
                PassType.Body.copy(
                    color = if (revealed) PassColorsDark.TextPrimary else PassColorsDark.TextDim,
                    letterSpacing = 0.04.em,
                ),
            cursorBrush = SolidColor(PassColorsDark.Accent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            visualTransformation = if (revealed) CharacterClassColors else PasswordVisualTransformation(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
                    .semantics { contentDescription = "edit password" },
        )
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            CardAction(label = if (revealed) "hide" else "show", enabled = true, onClick = onToggleReveal)
            VerticalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            CardAction(label = "copy", enabled = editor.password.isNotEmpty(), onClick = onCopy)
        }
    }
}

@Composable
private fun RowScope.CardAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PassType.Caption.copy(color = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint),
        )
    }
}

@Composable
private fun NotesEditor(
    editor: LoginEditorState,
    onNotesChange: (String) -> Unit,
) {
    val lineCount = editor.notes.lines().count { it.isNotBlank() }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("notes", style = PassType.Label)
        if (lineCount > 0) {
            Text(
                " · $lineCount line${if (lineCount > 1) "s" else ""}",
                style = PassType.Caption.copy(color = PassColorsDark.Accent),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Border2, PassShapes.small),
    ) {
        // Line 1 of the file is the password above, so the notes editor shows it as locked.
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
            Text("line 1 · password", style = PassType.Caption)
        }
        BasicTextField(
            value = editor.notes,
            onValueChange = onNotesChange,
            enabled = !editor.saving,
            singleLine = false,
            minLines = 3,
            textStyle = PassType.Body,
            cursorBrush = SolidColor(PassColorsDark.Accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics { contentDescription = "edit notes" },
            decorationBox = { innerTextField ->
                Box {
                    if (editor.notes.isEmpty()) {
                        Text(
                            "write anything\nrecovery codes, security answers…",
                            style = PassType.Body.copy(color = PassColorsDark.TextFaint),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
    Spacer(Modifier.height(6.dp))
    Text("saved as typed", style = PassType.Caption)
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
private fun SaveFooter(
    path: String,
    editor: LoginEditorState,
    onSave: () -> Unit,
) {
    val enabled = editor.changed && !editor.saving
    val lineCount = editor.notes.lines().count { it.isNotBlank() }
    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
    Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)) {
        editor.error?.let {
            Text(it, style = PassType.Caption.copy(color = PassColorsDark.Danger))
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(if (enabled) PassColorsDark.AccentDim else PassColorsDark.Border, PassShapes.small)
                    .border(1.dp, if (enabled) PassColorsDark.Accent else PassColorsDark.Border, PassShapes.small)
                    .clip(PassShapes.small)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onSave)
                    // Maestro taps the button by this description. The styled text below has no stable string.
                    .semantics(mergeDescendants = true) { contentDescription = "save entry" }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (editor.saving) savingText() else commandText(path, enabled),
                    style = PassType.Caption.copy(fontWeight = FontWeight.SemiBold),
                )
                if (lineCount > 0) {
                    Text(
                        "+ $lineCount line${if (lineCount > 1) "s" else ""} of notes",
                        style = PassType.Caption.copy(color = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint),
                    )
                }
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

private fun savingText(): AnnotatedString = AnnotatedString("saving…", SpanStyle(color = PassColorsDark.TextDim))

/** The `pass` command that matches the save, with the flags in the warning color. */
private fun commandText(
    path: String,
    enabled: Boolean,
): AnnotatedString {
    val prompt = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint
    val command = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint
    val flag = if (enabled) PassColorsDark.Warning else PassColorsDark.TextFaint
    return buildAnnotatedString {
        withStyle(SpanStyle(color = prompt)) { append("> ") }
        withStyle(SpanStyle(color = command)) { append("pass insert ") }
        withStyle(SpanStyle(color = flag)) { append("-m -f ") }
        withStyle(SpanStyle(color = command)) { append(path) }
    }
}

/** Colors digits with the accent and symbols with the warning color, as the new-entry design does. */
private object CharacterClassColors : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val colored =
            buildAnnotatedString {
                text.text.forEach { char ->
                    val color: Color? =
                        when {
                            char.isDigit() -> PassColorsDark.Accent
                            char.isLetter() -> null
                            else -> PassColorsDark.Warning
                        }
                    if (color == null) append(char) else withStyle(SpanStyle(color = color)) { append(char) }
                }
            }
        return TransformedText(colored, OffsetMapping.Identity)
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
