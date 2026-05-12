package com.example.pass.settings

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private enum class ActiveSheet { DefaultView, ClipboardTimeout, SessionTimeout }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onClearedData: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sshKey by viewModel.sshPublicKey.collectAsState()
    val remoteUrl by viewModel.remoteUrl.collectAsState()
    val sessionTimeout by viewModel.sessionTimeoutMinutes.collectAsState()
    val clipboardTimeout by viewModel.clipboardTimeoutSeconds.collectAsState()
    val defaultViewTree by viewModel.defaultViewTree.collectAsState()
    val gpgKeyInfo by viewModel.gpgKeyInfo.collectAsState()
    val sessionActive by viewModel.sessionActive.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeSheet(action: () -> Unit = {}) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { activeSheet = null }
    }

    PassScaffold(
        topBar = {
            SettingsTopBar(onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // GIT
            SettingsSection(label = "git") {
                MetaRow("remote url", if (remoteUrl.isNotEmpty()) remoteUrl else "not configured")
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                MetaRow(
                    "last sync",
                    state.lastSyncTime?.let { DATE_FMT.format(it) } ?: "never",
                )
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                if (sshKey != null) {
                    TappableRow(
                        label = "ssh key",
                        value = sshKey!!.substringBefore(" "),
                        onClick = {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, sshKey)
                                    },
                                    "share ssh key",
                                ),
                            )
                        },
                    )
                } else {
                    MetaRow("ssh key", "none")
                }
            }

            // GPG
            SettingsSection(label = "gpg") {
                if (gpgKeyInfo != null) {
                    MetaRow("key id", gpgKeyInfo!!.first)
                    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                    MetaRow("uid", gpgKeyInfo!!.second)
                } else {
                    MetaRow("key", "not imported")
                }
            }

            // DISPLAY
            SettingsSection(label = "display") {
                TappableRow(
                    label = "default view",
                    value = if (defaultViewTree) "tree" else "flat",
                    onClick = { activeSheet = ActiveSheet.DefaultView },
                )
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                TappableRow(
                    label = "clipboard timeout",
                    value = "${clipboardTimeout}s",
                    onClick = { activeSheet = ActiveSheet.ClipboardTimeout },
                )
            }

            // SESSION
            SettingsSection(label = "session") {
                TappableRow(
                    label = "session timeout",
                    value = sessionTimeoutLabel(sessionTimeout),
                    onClick = { activeSheet = ActiveSheet.SessionTimeout },
                )
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                DangerRow(label = "clear session", enabled = sessionActive, onClick = { viewModel.clearSession() })
            }

            // STORE
            SettingsSection(label = "store") {
                DangerRow(
                    label = if (state.clearing) "clearing…" else "delete local store",
                    enabled = !state.clearing,
                    onClick = { showClearConfirm = true },
                )
            }

            Text(
                "pass.android · v0.1.0 · linux pass compatible",
                style = PassType.Caption,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = PassColorsDark.Raised,
            contentColor = PassColorsDark.TextPrimary,
            scrimColor = Color(0x8C000000),
            dragHandle = { SheetDragHandle() },
        ) {
            when (activeSheet) {
                ActiveSheet.DefaultView -> {
                    SheetTitle("default view")
                    SheetOption(
                        label = "tree",
                        sub = "hierarchical folder view",
                        selected = defaultViewTree,
                        onClick = { closeSheet { viewModel.setDefaultView(true) } },
                    )
                    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                    SheetOption(
                        label = "flat",
                        sub = "sorted by last accessed",
                        selected = !defaultViewTree,
                        onClick = { closeSheet { viewModel.setDefaultView(false) } },
                    )
                    Spacer(Modifier.height(20.dp))
                }

                ActiveSheet.ClipboardTimeout -> {
                    SheetTitle("clipboard timeout")
                    listOf(15, 30, 45, 60).forEachIndexed { index, seconds ->
                        if (index > 0) HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                        TimeOption(
                            label = clipboardTimeoutLabel(seconds),
                            value = "${seconds}s",
                            selected = clipboardTimeout == seconds,
                            onClick = { closeSheet { viewModel.setClipboardTimeout(seconds) } },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                ActiveSheet.SessionTimeout -> {
                    SheetTitle("session timeout")
                    listOf(5, 15, 30, 60, 0).forEachIndexed { index, minutes ->
                        if (index > 0) HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                        TimeOption(
                            label = sessionTimeoutLabel(minutes),
                            value = if (minutes == 0) "∞" else "$minutes min",
                            selected = sessionTimeout == minutes,
                            onClick = { closeSheet { viewModel.setSessionTimeout(minutes) } },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                null -> {}
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = PassColorsDark.Surface,
            titleContentColor = PassColorsDark.Danger,
            textContentColor = PassColorsDark.TextDim,
            title = { Text("delete local store?", style = PassType.Title.copy(color = PassColorsDark.Danger)) },
            text = {
                Text(
                    "This will delete all keys, the cloned repository, and all preferences. You will need to go through onboarding again.",
                    style = PassType.Body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearAllData(onClearedData)
                }) {
                    Text("delete", style = PassType.Body.copy(color = PassColorsDark.Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("cancel", style = PassType.Body.copy(color = PassColorsDark.TextDim))
                }
            },
        )
    }
}

private fun sessionTimeoutLabel(minutes: Int): String =
    when (minutes) {
        0 -> "until app close"
        5 -> "5 minutes"
        15 -> "15 minutes"
        30 -> "30 minutes"
        60 -> "1 hour"
        else -> "$minutes min"
    }

private fun clipboardTimeoutLabel(seconds: Int): String =
    when (seconds) {
        15 -> "15 seconds"
        30 -> "30 seconds"
        45 -> "45 seconds"
        60 -> "1 minute"
        else -> "${seconds}s"
    }

// ── Sub-composables ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "back",
                tint = PassColorsDark.TextDim,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("settings", style = PassType.Title)
    }
}

@Composable
private fun MetaRow(
    key: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = PassType.Body, modifier = Modifier.weight(1f))
        Text(
            value,
            style = PassType.Caption.copy(color = PassColorsDark.TextDim),
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TappableRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PassType.Body, modifier = Modifier.weight(1f))
        Text(value, style = PassType.Caption.copy(color = PassColorsDark.TextDim), modifier = Modifier.padding(end = 6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PassColorsDark.TextFaint,
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun SettingsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(label, style = PassType.Label, modifier = Modifier.padding(bottom = 4.dp))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(PassColorsDark.Surface)
                    .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp)),
        ) { content() }
    }
}

@Composable
private fun DangerRow(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PassType.Body.copy(color = if (enabled) PassColorsDark.Danger else PassColorsDark.TextFaint),
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) PassColorsDark.Danger.copy(alpha = 0.5f) else PassColorsDark.TextFaint,
            modifier = Modifier.size(10.dp),
        )
    }
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .background(PassColorsDark.Border2, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        title,
        style = PassType.Label.copy(color = PassColorsDark.TextDim),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
}

@Composable
private fun SheetOption(
    label: String,
    sub: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (selected) PassColorsDark.AccentDim else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = PassType.Body.copy(color = if (selected) PassColorsDark.Accent else PassColorsDark.TextPrimary),
            )
            if (sub != null) {
                Text(sub, style = PassType.Caption, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = PassColorsDark.Accent, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun TimeOption(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (selected) PassColorsDark.AccentDim else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = PassType.Body.copy(color = if (selected) PassColorsDark.Accent else PassColorsDark.TextPrimary),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = PassColorsDark.Accent, modifier = Modifier.size(12.dp))
            }
        }
    }
}
