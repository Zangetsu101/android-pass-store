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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
fun EntryBrowserScreen(
    viewModel: EntryBrowserViewModel,
    onNavigateToEntryDetail: (PassEntry) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    val syncRotation by if (state.syncing) {
        rememberInfiniteTransition(label = "sync").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "rotation",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    PassScaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                .clickable { viewModel.pull() }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "sync",
                            tint = if (state.syncing) PassColorsDark.Accent else PassColorsDark.TextDim,
                            modifier = Modifier.size(11.dp).rotate(syncRotation),
                        )
                        Text("sync", style = PassType.Caption, color = PassColorsDark.TextDim)
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

            // Search bar / sync status banner (mutually exclusive, same slot)
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
                        "git pull --rebase origin main",
                        style = PassType.Body,
                        color = PassColorsDark.Accent,
                    )
                }
            } else {
                PassTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    prefix = "> grep -r ",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            collapsedDirs = state.collapsedDirs,
                            onToggleDir = viewModel::toggleDir,
                            onEntryClick = onNavigateToEntryDetail,
                            enabled = !state.syncing,
                        )
                    }

                    else -> {
                        FlatView(
                            entries = state.entries,
                            onEntryClick = onNavigateToEntryDetail,
                            enabled = !state.syncing,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatView(
    entries: List<PassEntry>,
    onEntryClick: (PassEntry) -> Unit,
    enabled: Boolean = true,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries) { listState.scrollToItem(0) }
    LazyColumn(state = listState) {
        itemsIndexed(entries, key = { _, it -> it.path }) { index, entry ->
            FlatEntryRow(entry = entry, index = index, enabled = enabled, onClick = { onEntryClick(entry) })
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
    enabled: Boolean = true,
) {
    val grouped =
        remember(entries) {
            entries.groupBy { entry ->
                val parts = entry.path.split("/")
                if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
            }
        }

    val listState = rememberLazyListState()
    LaunchedEffect(entries) { listState.scrollToItem(0) }
    LazyColumn(state = listState) {
        grouped.forEach { (dir, dirEntries) ->
            if (dir.isNotEmpty()) {
                item(key = "dir_$dir") {
                    DirHeader(
                        dir = dir,
                        collapsed = dir in collapsedDirs,
                        count = dirEntries.size,
                        enabled = enabled,
                        onToggle = { onToggleDir(dir) },
                    )
                }
            }
            if (dir.isEmpty() || dir !in collapsedDirs) {
                items(dirEntries, key = { it.path }) { entry ->
                    TreeEntryRow(entry = entry, indent = dir.isNotEmpty(), enabled = enabled, onClick = { onEntryClick(entry) })
                    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                }
            }
        }
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
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(start = if (indent) 40.dp else 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.username, style = PassType.Body, modifier = Modifier.weight(1f))
        Text("›", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}

@Composable
private fun FlatEntryRow(
    entry: PassEntry,
    index: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
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
        Text("›", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}
