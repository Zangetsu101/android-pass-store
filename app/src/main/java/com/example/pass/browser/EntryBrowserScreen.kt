package com.example.pass.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pass.passstore.PassEntry
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryBrowserScreen(
    viewModel: EntryBrowserViewModel,
    onNavigateToSync: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessionStart: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.sessionStartNeeded) {
        if (state.sessionStartNeeded) {
            onNavigateToSessionStart()
            viewModel.onSessionStartNavigated()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = viewModel.consumePendingDecryptEntry()
                if (pending != null) viewModel.requestDecrypt(pending, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PassScaffold { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("~/.password-store", style = PassType.Caption)
                Text("pass.android", style = PassType.Title)
            }
            Row {
                IconButton(onClick = { viewModel.toggleView() }) {
                    Icon(
                        if (state.treeView) Icons.Default.FormatListBulleted else Icons.Default.AccountTree,
                        contentDescription = if (state.treeView) "flat view" else "tree view",
                        tint = PassColorsDark.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onNavigateToSync) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "sync",
                        tint = PassColorsDark.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "settings",
                        tint = PassColorsDark.TextDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Search bar
        TextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = {
                Text(
                    if (state.treeView) "grep -r \"\"" else "grep -r \"\"",
                    style = PassType.Body.copy(color = PassColorsDark.TextDim),
                )
            },
            prefix = { Text("$ ", style = PassType.Body.copy(color = PassColorsDark.TextDim)) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PassColorsDark.Surface,
                unfocusedContainerColor = PassColorsDark.Surface,
                focusedTextColor = PassColorsDark.TextPrimary,
                unfocusedTextColor = PassColorsDark.TextPrimary,
                cursorColor = PassColorsDark.Accent,
                focusedIndicatorColor = PassColorsDark.Accent,
                unfocusedIndicatorColor = PassColorsDark.Border2,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp)),
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.entries.isEmpty() && state.searchQuery.isNotEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no results for '${state.searchQuery}'", style = PassType.Body)
                }
            }
            state.treeView -> {
                TreeView(
                    entries = state.entries,
                    collapsedDirs = state.collapsedDirs,
                    onToggleDir = viewModel::toggleDir,
                    onEntryClick = { viewModel.requestDecrypt(it, activity) },
                )
            }
            else -> {
                FlatView(
                    entries = state.entries,
                    onEntryClick = { viewModel.requestDecrypt(it, activity) },
                )
            }
        }
    }
    }

    if (state.decryptingEntry != null || state.credentials != null || state.decryptError != null) {
        EntryDetailSheet(
            state = state,
            onCopy = viewModel::copyPassword,
            onDismiss = viewModel::dismissDetail,
        )
    }
}

@Composable
private fun FlatView(entries: List<PassEntry>, onEntryClick: (PassEntry) -> Unit) {
    LazyColumn {
        items(entries, key = { it.path }) { entry ->
            FlatEntryRow(entry, onClick = { onEntryClick(entry) })
            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        }
    }
}

@Composable
private fun TreeView(
    entries: List<PassEntry>,
    collapsedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
    onEntryClick: (PassEntry) -> Unit,
) {
    val grouped = remember(entries) {
        entries.groupBy { entry ->
            val parts = entry.path.split("/")
            if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
        }
    }

    LazyColumn {
        grouped.forEach { (dir, dirEntries) ->
            if (dir.isNotEmpty()) {
                item(key = "dir_$dir") {
                    DirHeader(dir = dir, collapsed = dir in collapsedDirs, onToggle = { onToggleDir(dir) })
                }
            }
            if (dir.isEmpty() || dir !in collapsedDirs) {
                items(dirEntries, key = { it.path }) { entry ->
                    TreeEntryRow(entry = entry, indent = dir.isNotEmpty(), onClick = { onEntryClick(entry) })
                    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun DirHeader(dir: String, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (collapsed) "▶" else "▼",
            style = PassType.Body.copy(color = PassColorsDark.AccentMid),
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(text = dir, style = PassType.Body.copy(color = PassColorsDark.Accent.copy(alpha = 0.75f)))
    }
}

@Composable
private fun TreeEntryRow(entry: PassEntry, indent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 40.dp else 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.username, style = PassType.Body, modifier = Modifier.weight(1f))
        Text("›", style = PassType.Body.copy(color = PassColorsDark.TextFaint))
    }
}

@Composable
private fun FlatEntryRow(entry: PassEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.username, style = PassType.Body)
            entry.domain?.let { Text(it, style = PassType.Caption) }
        }
        Text("›", style = PassType.Body.copy(color = PassColorsDark.TextFaint))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailSheet(
    state: EntryBrowserUiState,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PassColorsDark.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val entry = state.decryptingEntry
            if (entry != null) {
                Text(entry.username, style = PassType.Title)
                entry.domain?.let { Text(it, style = PassType.Caption) }
                Spacer(Modifier.height(16.dp))
            }

            when {
                state.decryptError != null -> {
                    Text("error: ${state.decryptError}", color = PassColorsDark.Danger, style = PassType.Body)
                }
                state.credentials == null -> {
                    Text("authenticating…", style = PassType.Body)
                }
                else -> {
                    val creds = state.credentials
                    Text("PASSWORD", style = PassType.Label)
                    Spacer(Modifier.height(4.dp))
                    Text(text = String(creds.password), style = PassType.Body.copy(color = PassColorsDark.Accent))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCopy,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = if (state.clipboardCopied) PassColorsDark.Accent else PassColorsDark.TextDim,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.clipboardCopied) PassColorsDark.Accent else PassColorsDark.Border2,
                        ),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (state.clipboardCopied) "copied · clears in 45s" else "copy password",
                            style = PassType.Body,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    if (creds.notes.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("NOTES", style = PassType.Label)
                        Spacer(Modifier.height(4.dp))
                        Text(creds.notes, style = PassType.Body)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("close", style = PassType.Caption.copy(color = PassColorsDark.TextDim))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
