package com.example.pass.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            // Autofill
            Text("Autofill", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Configure autofill service")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Session timeout
            Text("Session timeout", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${sessionTimeout} minute${if (sessionTimeout == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = sessionTimeout.toFloat(),
                onValueChange = { viewModel.setSessionTimeout(it.roundToInt()) },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Text("1 min", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text("60 min", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // SSH public key
            Text("SSH public key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sshKey != null) {
                Text(
                    text = sshKey!!,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(sshKey!!)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy SSH key")
                }
            } else {
                Text("No SSH key generated yet.", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // Clear all data
            Text("Danger zone", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showClearConfirm = true },
                enabled = !state.clearing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.clearing) "Clearing…" else "Clear all data")
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = {
                Text("This will delete all keys, the cloned repository, and all preferences. You will need to go through onboarding again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearAllData(onClearedData)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
