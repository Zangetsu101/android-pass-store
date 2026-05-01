package com.example.pass.syncpanel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPanelScreen(viewModel: SyncPanelViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("sync", style = PassType.Title) },
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
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            Text("LAST SYNC", style = PassType.Label)
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.lastSyncTime?.format() ?: "never",
                style = PassType.Body,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("REMOTE  ", style = PassType.Label)
                Text(
                    text = when (state.remoteReachable) {
                        true  -> "reachable"
                        false -> "unreachable"
                        null  -> "unknown"
                    },
                    style = PassType.Body.copy(
                        color = when (state.remoteReachable) {
                            true  -> PassColorsDark.Accent
                            false -> PassColorsDark.Danger
                            null  -> PassColorsDark.TextDim
                        },
                    ),
                )
            }

            Spacer(Modifier.height(24.dp))

            state.pullError?.let { err ->
                Text("error: $err", color = PassColorsDark.Danger, style = PassType.Caption)
                Spacer(Modifier.height(8.dp))
            }

            if (state.pullSuccess) {
                Text("sync complete.", color = PassColorsDark.Accent, style = PassType.Caption)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = viewModel::pull,
                enabled = !state.pulling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PassColorsDark.AccentDim,
                    contentColor = PassColorsDark.Accent,
                    disabledContainerColor = PassColorsDark.Border,
                    disabledContentColor = PassColorsDark.TextFaint,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, PassColorsDark.Accent, MaterialTheme.shapes.small),
            ) {
                if (state.pulling) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = PassColorsDark.Accent,
                    )
                }
                Text(
                    if (state.pulling) "pulling…" else "$ pull now",
                    style = PassType.Body.copy(color = PassColorsDark.Accent),
                )
            }
        }
    }
}

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

private fun Instant.format(): String = formatter.format(this)
