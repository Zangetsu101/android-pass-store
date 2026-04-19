package com.example.pass.syncpanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPanelScreen(viewModel: SyncPanelViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sync") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(Modifier.padding(24.dp)) {
            Text("Last sync", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.lastSyncTime?.format() ?: "Never",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remote status: ", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = when (state.remoteReachable) {
                        true -> "Reachable"
                        false -> "Unreachable"
                        null -> "Unknown"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.remoteReachable) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.outline
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            if (state.pullError != null) {
                Text(
                    "Error: ${state.pullError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.pullSuccess) {
                Text(
                    "Sync complete.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = viewModel::pull,
                enabled = !state.pulling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.pulling) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (state.pulling) "Pulling…" else "Pull now")
            }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private fun Instant.format(): String = formatter.format(this)
