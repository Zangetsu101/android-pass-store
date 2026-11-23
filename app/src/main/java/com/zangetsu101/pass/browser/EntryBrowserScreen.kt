// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.components.PassTag
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun EntryBrowserScreen(
    viewModel: EntryBrowserViewModel,
    onNavigateToEntryDetail: (PassEntry) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSyncPanel: () -> Unit = {},
    onNewEntry: (String) -> Unit = {},
    addedPath: String? = null,
    onAddedPathHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    val chipBusy = state.syncing || sync.command != null
    val lastSync = sync.status.lastSyncTime
    val now by produceState(Instant.now(), lastSync) {
        while (true) {
            value = Instant.now()
            delay(15_000)
        }
    }
    val showSuccess by produceState(false, sync.completed, lastSync) {
        value = sync.completed && lastSync != null && Duration.between(lastSync, Instant.now()).seconds < 4
        if (value) {
            delay(4_000)
            value = false
        }
    }
    val (syncLabel, chipColor) =
        when {
            chipBusy -> (if (state.syncing) "syncing…" else "pushing…") to PassColorsDark.Accent
            sync.error != null -> "error" to PassColorsDark.Danger
            sync.status.unpushed.isNotEmpty() -> "↑${sync.status.unpushed.size} unpushed" to PassColorsDark.TextDim
            sync.status.isSynced && lastSync != null -> "synced · ${syncAge(lastSync, now)}" to PassColorsDark.Accent
            sync.status.isSynced -> "synced" to PassColorsDark.Accent
            else -> "sync" to PassColorsDark.TextDim
        }

    val syncRotation by if (chipBusy) {
        rememberInfiniteTransition(label = "sync").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "rotation",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(addedPath) {
        if (addedPath != null) {
            viewModel.selectEntry("$addedPath.gpg")
            snackbar.showSnackbar("added $addedPath")
            onAddedPathHandled()
        }
    }

    PassScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.syncing) {
                FloatingActionButton(
                    onClick = { onNewEntry(state.searchQuery) },
                    modifier =
                        Modifier
                            .size(44.dp)
                            .dropShadow(PassShapes.small, Shadow(radius = 20.dp, color = PassColorsDark.AccentMid))
                            .border(1.dp, PassColorsDark.Accent, PassShapes.small),
                    shape = PassShapes.small,
                    containerColor = PassColorsDark.AccentDim.compositeOver(PassColorsDark.Background),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    contentColor = PassColorsDark.Accent,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "add entry", modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Top bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("~/.password-store", style = PassType.Caption)
                    Row {
                        Text("pass", style = PassType.Title, color = PassColorsDark.Accent)
                        Text(".android", style = PassType.Title.copy(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier =
                            Modifier
                                .background(PassColorsDark.Surface, PassShapes.small)
                                .border(1.dp, PassColorsDark.Border2, PassShapes.small)
                                .clickable(enabled = !chipBusy, onClick = viewModel::sync)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "sync",
                            tint = chipColor,
                            modifier = Modifier.size(11.dp).rotate(syncRotation),
                        )
                        Text(syncLabel, style = PassType.Caption, color = chipColor, maxLines = 1)
                    }
                    IconButton(onClick = { viewModel.toggleView() }) {
                        Icon(
                            if (state.treeView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.AccountTree,
                            contentDescription = if (state.treeView) "flat view" else "tree view",
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
            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))

            if (state.syncing) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(PassColorsDark.Surface, PassShapes.small)
                            .border(1.dp, PassColorsDark.AccentMid, PassShapes.small)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = PassColorsDark.Accent,
                        modifier = Modifier.size(10.dp).rotate(syncRotation),
                    )
                    Text(
                        sync.command ?: "syncing…",
                        style = PassType.Body,
                        color = PassColorsDark.Accent,
                    )
                }
            } else {
                val error = sync.error?.message
                if (error != null) {
                    Text(
                        "$error · view details ›",
                        style = PassType.Body,
                        color = PassColorsDark.Danger,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .background(PassColorsDark.Surface, PassShapes.small)
                                .border(1.dp, PassColorsDark.Danger, PassShapes.small)
                                .clickable(onClick = onNavigateToSyncPanel)
                                .padding(12.dp),
                    )
                } else if (showSuccess) {
                    Text(
                        "✓ sync complete",
                        style = PassType.Body,
                        color = PassColorsDark.Accent,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                PassTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    prefix = "$ grep -r ",
                    placeholder = "\"\"",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }

            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)

            Box(modifier = Modifier.alpha(if (state.syncing) 0.45f else 1f)) {
                when {
                    state.entries.isEmpty() && state.searchQuery.isNotEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("no results for '${state.searchQuery}'", style = PassType.Body)
                        }
                    }

                    state.treeView -> {
                        TreeView(
                            entries = state.entries,
                            selectedPath = state.selectedPath,
                            collapsedDirs = state.collapsedDirs,
                            onToggleDir = viewModel::toggleDir,
                            onEntryClick = onNavigateToEntryDetail,
                            enabled = !state.syncing,
                        )
                    }

                    else -> {
                        FlatView(
                            entries = state.entries,
                            selectedPath = state.selectedPath,
                            onEntryClick = onNavigateToEntryDetail,
                            enabled = !state.syncing,
                        )
                    }
                }
            }
        }
    }
}

private fun syncAge(
    lastSync: Instant,
    now: Instant,
): String {
    val minutes = Duration.between(lastSync, now).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}

@Composable
private fun FlatView(
    entries: List<PassEntry>,
    selectedPath: String?,
    onEntryClick: (PassEntry) -> Unit,
    enabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries, selectedPath) {
        val selectedIndex = entries.indexOfFirst { it.path == selectedPath }
        if (entries.isNotEmpty()) listState.scrollToItem(selectedIndex.coerceAtLeast(0))
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 64.dp)) {
        items(entries, key = { it.path }) { entry ->
            FlatEntryRow(
                entry = entry,
                selected = entry.path == selectedPath,
                enabled = enabled,
                onClick = { onEntryClick(entry) },
            )
            HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        }
    }
}

@Composable
private fun TreeView(
    entries: List<PassEntry>,
    selectedPath: String?,
    collapsedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
    onEntryClick: (PassEntry) -> Unit,
    enabled: Boolean = true,
) {
    val grouped = remember(entries) { entries.groupBy { it.path.substringBeforeLast('/', "") } }
    val rows =
        remember(grouped, collapsedDirs) {
            buildList {
                for ((directory, directoryEntries) in grouped) {
                    val collapsed = directory.isNotEmpty() && directory in collapsedDirs
                    if (directory.isNotEmpty()) add(TreeRow.Directory(directory, directoryEntries.size, collapsed))
                    if (!collapsed) directoryEntries.forEach { add(TreeRow.Entry(it, indent = directory.isNotEmpty())) }
                }
            }
        }

    val listState = rememberLazyListState()
    val selectedIndex = if (selectedPath == null) -1 else rows.indexOfFirst { it is TreeRow.Entry && it.entry.path == selectedPath }
    val hasSelectedRow = selectedIndex >= 0
    LaunchedEffect(grouped, selectedPath, hasSelectedRow) {
        if (hasSelectedRow) {
            listState.scrollToItem(selectedIndex)
        } else if (selectedPath == null && rows.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 64.dp)) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is TreeRow.Directory -> {
                    DirHeader(
                        dir = row.path,
                        collapsed = row.collapsed,
                        count = row.count,
                        enabled = enabled,
                        onToggle = { onToggleDir(row.path) },
                    )
                }

                is TreeRow.Entry -> {
                    TreeEntryRow(
                        entry = row.entry,
                        indent = row.indent,
                        selected = row.entry.path == selectedPath,
                        enabled = enabled,
                        onClick = { onEntryClick(row.entry) },
                    )
                    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                }
            }
        }
    }
}

private sealed interface TreeRow {
    val key: String

    class Directory(
        val path: String,
        val count: Int,
        val collapsed: Boolean,
    ) : TreeRow {
        override val key = "directory:$path"
    }

    class Entry(
        val entry: PassEntry,
        val indent: Boolean,
    ) : TreeRow {
        override val key = "entry:${entry.path}"
    }
}

@Composable
private fun DirHeader(
    dir: String,
    collapsed: Boolean,
    count: Int,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (collapsed) "▶" else "▼",
            style = PassType.Caption.copy(color = PassColorsDark.AccentMid),
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text = "$dir/", style = PassType.Body.copy(color = PassColorsDark.Accent.copy(alpha = 0.75f)), modifier = Modifier.weight(1f))
        Text("$count", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}

@Composable
private fun TreeEntryRow(
    entry: PassEntry,
    indent: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .entrySelection(selected)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(start = if (indent) 40.dp else 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.username, style = PassType.Body, modifier = Modifier.weight(1f))
        if (entry.isCard) PassTag("card", Modifier.padding(end = 8.dp))
        Text("›", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}

@Composable
private fun FlatEntryRow(
    entry: PassEntry,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .entrySelection(selected)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.username, style = PassType.Body)
            entry.domain?.let {
                Text(it, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
            }
        }
        if (entry.isCard) PassTag("card", Modifier.padding(end = 8.dp))
        Text("›", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}

private fun Modifier.entrySelection(isSelected: Boolean): Modifier =
    background(if (isSelected) PassColorsDark.AccentDim else Color.Transparent)
        .semantics { selected = isSelected }
