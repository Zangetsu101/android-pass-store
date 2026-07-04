// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.R
import com.zangetsu101.pass.ui.components.PassAppIcon
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.components.PassTextField
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassType

@Composable
fun SessionStartScreen(
    viewModel: SessionStartViewModel,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showPassphrase by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    LaunchedEffect(state.success) {
        if (state.success) onSuccess()
    }

    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PassAppIcon(size = 52.dp)
                Spacer(Modifier.height(20.dp))
                Text(state.title, style = PassType.Title, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "enter your gpg key passphrase\nto start a new session",
                    style = PassType.Body.copy(color = PassColorsDark.TextDim),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Text("passphrase", style = PassType.Label, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(6.dp))
                PassTextField(
                    value = state.passphrase,
                    onValueChange = viewModel::setPassphrase,
                    placeholder = "enter passphrase",
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    isError = state.error != null,
                    enabled = !state.loading,
                    trailingIcon = {
                        Icon(
                            painter =
                                painterResource(
                                    if (showPassphrase) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                ),
                            contentDescription = null,
                            tint = PassColorsDark.TextDim,
                            modifier =
                                Modifier
                                    .size(13.dp)
                                    .clickable { showPassphrase = !showPassphrase },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = PassType.Caption,
                        color = PassColorsDark.Danger,
                        modifier = Modifier.align(Alignment.Start),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "session lasts ${state.sessionTimeoutMinutes} min · configurable in settings",
                    style = PassType.Caption.copy(color = PassColorsDark.TextFaint),
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.loading && state.passphrase.isNotEmpty(),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PassColorsDark.AccentDim,
                            contentColor = PassColorsDark.Accent,
                            disabledContainerColor = PassColorsDark.Border,
                            disabledContentColor = PassColorsDark.TextFaint,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .border(
                                1.dp,
                                if (!state.loading && state.passphrase.isNotEmpty()) {
                                    PassColorsDark.Accent
                                } else {
                                    PassColorsDark.Border
                                },
                                MaterialTheme.shapes.extraSmall,
                            ),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PassColorsDark.Accent,
                        )
                    } else {
                        Text("unlock session", style = PassType.Body.copy(color = PassColorsDark.Accent))
                    }
                }
            }
        }
    }
}