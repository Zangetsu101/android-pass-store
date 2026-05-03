package com.example.pass.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassSecondaryButton
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassShapes
import com.example.pass.ui.theme.PassType
import kotlinx.coroutines.delay

@Composable
fun CloneProgressScreen(
    viewModel: CloneProgressViewModel,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val logState = rememberLazyListState()
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    LaunchedEffect(state.cloneComplete) { if (state.cloneComplete) onSuccess() }
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) logState.animateScrollToItem(state.logs.size - 1)
    }

    OnboardingScaffold(
        step = 3,
        total = 3,
        title = "cloning store",
        subtitle = viewModel.remoteUrl,
        scrollable = false,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(PassColorsDark.Surface, PassShapes.medium)
                    .border(1.dp, PassColorsDark.Border2, PassShapes.medium)
                    .padding(12.dp),
        ) {
            LazyColumn(state = logState, modifier = Modifier.fillMaxSize()) {
                item { Text("> git clone ${viewModel.remoteUrl}", style = PassType.Caption, color = PassColorsDark.TextPrimary) }
                itemsIndexed(state.logs) { index, entry ->
                    val isLast = index == state.logs.size - 1
                    val label =
                        when (entry) {
                            is LogEntry.Simple -> {
                                entry.text
                            }

                            is LogEntry.Progress -> {
                                if (entry.total > 0) {
                                    val pct = (entry.completed * 100f / entry.total).toInt()
                                    "${entry.name}: $pct% (${entry.completed}/${entry.total})"
                                } else {
                                    "${entry.name}: ${entry.completed}"
                                }
                            }
                        }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = PassType.Caption, color = if (isLast) PassColorsDark.AccentMid else PassColorsDark.TextDim)
                        if (isLast && state.cloning && cursorVisible) {
                            Spacer(Modifier.width(4.dp))
                            val cursorHeight = with(LocalDensity.current) { 9.sp.toDp() }
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 6.dp, height = cursorHeight)
                                        .background(PassColorsDark.Accent),
                            )
                        }
                    }
                }
            }
        }
        state.cloneError?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text(err, color = PassColorsDark.Danger, style = PassType.Caption)
            Spacer(Modifier.height(8.dp))
            PassPrimaryButton(onClick = { viewModel.retryClone() }, label = "$ retry")
        }
        if (state.cloning) {
            Spacer(Modifier.height(12.dp))
            PassSecondaryButton(
                onClick = { viewModel.cancelClone() },
                label = "cancel",
            )
        }
    }
}
