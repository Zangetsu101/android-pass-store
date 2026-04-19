package com.example.pass.browser

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.pass.passstore.PassEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryBrowserScreen(viewModel: EntryBrowserViewModel) {
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as FragmentActivity
    var searchActive by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pass", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { viewModel.toggleView() }) {
                Icon(
                    if (state.treeView) Icons.Default.FormatListBulleted else Icons.Default.AccountTree,
                    contentDescription = if (state.treeView) "Switch to flat" else "Switch to tree",
                )
            }
        }

        SearchBar(
            query = state.searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            onSearch = {},
            active = searchActive,
            onActiveChange = { searchActive = it },
            placeholder = { Text("Search entries…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {}

        Spacer(Modifier.height(8.dp))

        if (state.entries.isEmpty() && state.searchQuery.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results for "${state.searchQuery}"", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (state.treeView) {
            TreeView(
                entries = state.entries,
                collapsedDirs = state.collapsedDirs,
                onToggleDir = viewModel::toggleDir,
                onEntryClick = { viewModel.requestDecrypt(it, activity) },
            )
        } else {
            FlatView(
                entries = state.entries,
                onEntryClick = { viewModel.requestDecrypt(it, activity) },
            )
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
private fun FlatView(
    entries: List<PassEntry>,
    onEntryClick: (PassEntry) -> Unit,
) {
    LazyColumn {
        items(entries, key = { it.path }) { entry ->
            EntryRow(entry, onClick = { onEntryClick(entry) })
            HorizontalDivider()
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
                    EntryRow(
                        entry = entry,
                        indent = dir.isNotEmpty(),
                        onClick = { onEntryClick(entry) },
                    )
                    HorizontalDivider()
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = dir,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun EntryRow(entry: PassEntry, indent: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.username) },
        supportingContent = entry.domain?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = if (indent) 24.dp else 0.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailSheet(
    state: EntryBrowserUiState,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            val entry = state.decryptingEntry
            if (entry != null) {
                Text(entry.username, style = MaterialTheme.typography.titleLarge)
                entry.domain?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(16.dp))
            }

            when {
                state.decryptError != null -> {
                    Text(
                        "Error: ${state.decryptError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.credentials == null -> {
                    Text("Authenticating…", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    val creds = state.credentials
                    Text("Password", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String(creds.password),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text(
                            text = if (state.clipboardCopied) "Copied! (clears in 45s)" else "Copy password",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (creds.notes.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Notes", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(creds.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
