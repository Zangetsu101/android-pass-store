// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.syncpanel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.gitsync.EntryChangeAction
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPanelScreen(
    viewModel: SyncPanelViewModel,
    onBack: () -> Unit,
) {
    val sync by viewModel.syncState.collectAsState()
    val canDiscard = sync.error is SyncError.Conflict && sync.status.unpushed.isNotEmpty()
    val discard by viewModel.discardState.collectAsState()
    var confirmation by remember { mutableStateOf<SyncStatus?>(null) }

    BackHandler(enabled = discard.running) {}
    LaunchedEffect(discard.done) {
        if (discard.done) onBack()
    }

    PassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("sync recovery", style = PassType.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !discard.running) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = PassColorsDark.Background,
                        titleContentColor = PassColorsDark.Accent,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sync.error?.message?.let { Text(it, style = PassType.Body, color = PassColorsDark.Danger) }
                    if (canDiscard) {
                        Text(
                            "To use the remote version, discard your unpushed changes. You can review the changes before you confirm.",
                            style = PassType.Body,
                        )
                    }
                }
            }
            item { Text("UNPUSHED COMMITS (${sync.status.unpushed.size})", style = PassType.Label) }
            items(sync.status.unpushed) { commit ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(commit.message, style = PassType.Body)
                    for (path in commit.paths) {
                        Text(path, style = PassType.Caption, color = PassColorsDark.TextDim)
                    }
                }
            }
            item {
                discard.error?.let { Text(it, style = PassType.Body, color = PassColorsDark.Danger) }
                if (canDiscard) {
                    TextButton(onClick = { confirmation = sync.status }, enabled = !discard.running) {
                        Text(
                            if (discard.running) "discarding…" else "discard my unpushed changes",
                            color = PassColorsDark.Danger,
                        )
                    }
                }
            }
        }
    }

    confirmation?.let { expected ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Discard unpushed changes?") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text("The remote versions will replace your local changes. You must make your edits again.") }
                    items(expected.discardPaths) { change ->
                        val action =
                            when (change.action) {
                                EntryChangeAction.ADDED -> "added"
                                EntryChangeAction.DELETED -> "deleted"
                                EntryChangeAction.EDITED -> "edited"
                            }
                        Text("${change.path} · $action", style = PassType.Body)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    viewModel.discardUnpushed(expected)
                }) {
                    Text("Discard changes", color = PassColorsDark.Danger)
                }
            },
        )
    }
}
