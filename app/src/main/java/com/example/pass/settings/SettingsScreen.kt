package com.example.pass.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.components.PassSecondaryButton
import com.example.pass.ui.components.PassToggle
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

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
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    PassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("settings", style = PassType.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PassColorsDark.TextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PassColorsDark.Background,
                    titleContentColor = PassColorsDark.Accent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // GIT
            SettingsSection(label = "GIT") {
                MetaRow("remote", if (remoteUrl.isNotEmpty()) remoteUrl else "not configured")
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                MetaRow(
                    "last sync",
                    state.lastSyncTime?.let { DATE_FMT.format(it) } ?: "never",
                )
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                if (sshKey != null) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("SSH KEY", style = PassType.Label)
                        Spacer(Modifier.height(6.dp))
                        Text(sshKey!!, style = PassType.Caption, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        PassSecondaryButton(
                            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", sshKey!!))) } },
                            label = "copy ssh key",
                        )
                    }
                } else {
                    MetaRow("ssh key", "none")
                }
            }

            // GPG
            SettingsSection(label = "GPG") {
                MetaRow("key", "imported (fingerprint not shown)")
            }

            // DISPLAY
            SettingsSection(label = "DISPLAY") {
                SettingsRow {
                    Text("theme", style = PassType.Body, modifier = Modifier.weight(1f))
                    Text("dark (light coming soon)", style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                }
            }

            // SESSION
            SettingsSection(label = "SESSION") {
                SettingsRow {
                    Text("lock session", style = PassType.Body, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.lockSession() }) {
                        Text("lock →", style = PassType.Caption.copy(color = PassColorsDark.Accent))
                    }
                }
                HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
                SettingsRow {
                    Text("inactivity timeout", style = PassType.Body, modifier = Modifier.weight(1f))
                    PassToggle(
                        checked = sessionTimeout > 0,
                        onCheckedChange = { viewModel.setSessionTimeoutEnabled(it) },
                    )
                }
                if (sessionTimeout > 0) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            "$sessionTimeout min${if (sessionTimeout == 1) "" else "s"}",
                            style = PassType.Caption,
                        )
                        Slider(
                            value = sessionTimeout.toFloat(),
                            onValueChange = { viewModel.setSessionTimeout(it.roundToInt()) },
                            valueRange = 1f..60f,
                            steps = 58,
                            colors = SliderDefaults.colors(
                                thumbColor = PassColorsDark.Accent,
                                activeTrackColor = PassColorsDark.Accent,
                                inactiveTrackColor = PassColorsDark.Border2,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            Text("1 min", style = PassType.Caption)
                            Spacer(Modifier.weight(1f))
                            Text("60 min", style = PassType.Caption)
                        }
                    }
                }
            }

            // STORE
            SettingsSection(label = "STORE") {
                SettingsRow {
                    PassDangerButton(
                        onClick = { showClearConfirm = true },
                        enabled = !state.clearing,
                        label = if (state.clearing) "clearing…" else "clear all data",
                    )
                }
            }

            Text(
                "pass.android · v0.1.0 · linux pass compatible",
                style = PassType.Caption,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = PassColorsDark.Surface,
            titleContentColor = PassColorsDark.Danger,
            textContentColor = PassColorsDark.TextDim,
            title = { Text("clear all data?", style = PassType.Title.copy(color = PassColorsDark.Danger)) },
            text = {
                Text(
                    "This will delete all keys, the cloned repository, and all preferences. You will need to go through onboarding again.",
                    style = PassType.Body,
                )
            },
            confirmButton = {
                PassDangerButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllData(onClearedData)
                    },
                    label = "clear all",
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("cancel", style = PassType.Body.copy(color = PassColorsDark.TextDim))
                }
            },
        )
    }
}

@Composable
private fun MetaRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = PassType.Caption.copy(color = PassColorsDark.TextDim))
        Text(
            value,
            style = PassType.Caption,
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = PassType.Label, modifier = Modifier.padding(bottom = 4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(PassColorsDark.Surface)
                .border(1.dp, PassColorsDark.Border2, RoundedCornerShape(4.dp)),
        ) { content() }
    }
}

@Composable
private fun PassDangerButton(
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = PassColorsDark.AccentDim.copy(alpha = 0.12f),
            contentColor = PassColorsDark.Danger,
            disabledContainerColor = PassColorsDark.Border,
            disabledContentColor = PassColorsDark.TextFaint,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, if (enabled) PassColorsDark.Danger else PassColorsDark.Border, MaterialTheme.shapes.extraSmall),
    ) {
        Text(label, style = PassType.Body.copy(color = if (enabled) PassColorsDark.Danger else PassColorsDark.TextFaint))
    }
}

@Composable
private fun SettingsRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
