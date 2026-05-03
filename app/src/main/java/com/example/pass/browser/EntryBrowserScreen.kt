package com.example.pass.browser

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pass.passstore.PassEntry
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

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
        remember { mutableStateOf(0f) }
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
                    Row {
                        Text("pass", style = PassType.Title, color = PassColorsDark.Accent)
                        Text(".android", style = PassType.Title.copy(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .background(PassColorsDark.Surface, RoundedCornerShape(6.dp))
                            .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(6.dp))
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
                            if (state.treeView) Icons.Default.FormatListBulleted else Icons.Default.AccountTree,
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

            // Sync status banner
            if (state.syncMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PassColorsDark.Surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$ ${state.syncMessage}",
                        style = PassType.Caption,
                        color = PassColorsDark.TextDim,
                    )
                }
            }

            // Search bar
            TextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = {
                    Text(
                        "grep -r \"\"",
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
                        )
                    }
                    else -> {
                        FlatView(
                            entries = state.entries,
                            onEntryClick = onNavigateToEntryDetail,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatView(entries: List<PassEntry>, onEntryClick: (PassEntry) -> Unit) {
    LazyColumn {
        itemsIndexed(entries, key = { _, it -> it.path }) { index, entry ->
            FlatEntryRow(entry = entry, index = index, onClick = { onEntryClick(entry) })
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
                    DirHeader(dir = dir, collapsed = dir in collapsedDirs, count = dirEntries.size, onToggle = { onToggleDir(dir) })
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
private fun DirHeader(dir: String, collapsed: Boolean, count: Int, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 8.dp),
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
private fun TreeEntryRow(entry: PassEntry, indent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (indent) 40.dp else 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = entry.username, style = PassType.Body, modifier = Modifier.weight(1f))
        Text("›", style = PassType.Caption.copy(color = PassColorsDark.TextFaint))
    }
}

@Composable
private fun FlatEntryRow(entry: PassEntry, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
