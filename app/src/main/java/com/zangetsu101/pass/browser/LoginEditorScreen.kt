// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassPrimaryButton
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassType

@Composable
internal fun LoginEditorScreen(
    entry: PassEntry,
    editor: LoginEditorState,
    viewModel: EntryDetailViewModel,
) {
    val path = entry.path.removeSuffix(".gpg")
    var revealed by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val back = {
        if (!editor.saving) {
            if (editor.changed) confirmDiscard = true else viewModel.discardEditor()
        }
    }
    BackHandler(onBack = back)

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = back, enabled = !editor.saving) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "back")
                }
                Column {
                    Text("edit entry", style = PassType.Title)
                    Text(path, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                }
            }
            HorizontalDivider(color = PassColorsDark.Border)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("password", style = PassType.Body)
                PassTextField(
                    value = editor.password,
                    onValueChange = viewModel::updateEditorPassword,
                    modifier = Modifier.semantics { contentDescription = "edit password" },
                    enabled = !editor.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                )
                Row {
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(if (revealed) "hide" else "show")
                    }
                    TextButton(onClick = viewModel::copyEditorPassword, enabled = editor.password.isNotEmpty()) {
                        Text("copy")
                    }
                }

                Text("notes", style = PassType.Body)
                Text("•••••••••• · line 1 · password", style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                PassTextField(
                    value = editor.notes,
                    onValueChange = viewModel::updateEditorNotes,
                    modifier = Modifier.semantics { contentDescription = "edit notes" },
                    singleLine = false,
                    enabled = !editor.saving,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    minLines = 5,
                    maxLines = 10,
                )
                if (editor.hasOtp) Text("otp · kept as-is", style = PassType.Caption)
                editor.error?.let { Text(it, style = PassType.Body.copy(color = PassColorsDark.Danger)) }
                PassPrimaryButton(
                    label = if (editor.saving) "saving…" else "> pass insert -m -f $path",
                    onClick = viewModel::saveEditor,
                    enabled = editor.changed && !editor.saving,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("discard changes to $path?") },
            confirmButton = { TextButton(onClick = viewModel::discardEditor) { Text("discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("keep editing") } },
        )
    }
}
