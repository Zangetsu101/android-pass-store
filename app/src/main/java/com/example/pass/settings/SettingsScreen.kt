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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.PassToggle
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onClearedData: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sshKey by viewModel.sshPublicKey.collectAsState()
    val sessionTimeout by viewModel.sessionTimeoutMinutes.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("settings", style = PassType.Title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PassColorsDark.TextDim,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = PassColorsDark.Background,
                titleContentColor = PassColorsDark.Accent,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSection(label = "AUTOFILL") {
                SettingsRow {
                    Text("configure autofill service", style = PassType.Body, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            })
                        },
                    ) {
                        Text("open →", style = PassType.Caption.copy(color = PassColorsDark.Accent))
                    }
                }
            }

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

            SettingsSection(label = "SSH KEY") {
                if (sshKey != null) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = sshKey!!,
                            style = PassType.Caption,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(sshKey!!)) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColorsDark.TextDim),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PassColorsDark.Border2),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                        ) {
                            Text("copy ssh key", style = PassType.Caption.copy(color = PassColorsDark.TextDim))
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("no ssh key generated yet.", style = PassType.Caption)
                    }
                }
            }

            SettingsSection(label = "STORE") {
                SettingsRow {
                    Button(
                        onClick = { showClearConfirm = true },
                        enabled = !state.clearing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PassColorsDark.AccentDim.copy(alpha = 0.12f),
                            contentColor = PassColorsDark.Danger,
                            disabledContainerColor = PassColorsDark.Border,
                            disabledContentColor = PassColorsDark.TextFaint,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(1.dp, PassColorsDark.Danger, MaterialTheme.shapes.small),
                    ) {
                        Text(
                            if (state.clearing) "clearing…" else "delete local store",
                            style = PassType.Body.copy(color = PassColorsDark.Danger),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
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
            title = { Text("delete local store?", style = PassType.Title.copy(color = PassColorsDark.Danger)) },
            text = {
                Text(
                    "This will delete all keys, the cloned repository, and all preferences. You will need to go through onboarding again.",
                    style = PassType.Body,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllData(onClearedData)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PassColorsDark.AccentDim.copy(alpha = 0.12f),
                        contentColor = PassColorsDark.Danger,
                    ),
                    modifier = Modifier.border(1.dp, PassColorsDark.Danger, MaterialTheme.shapes.small),
                ) {
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
private fun SettingsRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
