package com.example.pass.session

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.pass.ui.components.passTextFieldColors
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@Composable
fun SessionStartScreen(
    viewModel: SessionStartViewModel,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("unlock session", style = PassType.Title)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your GPG key passphrase to start a session.",
            style = PassType.Body,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.passphrase,
            onValueChange = viewModel::setPassphrase,
            label = { Text("passphrase", style = PassType.Caption) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it, style = PassType.Caption, color = PassColorsDark.Danger) } },
            enabled = !state.loading,
            colors = passTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.loading && state.passphrase.isNotEmpty(),
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
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = PassColorsDark.Accent,
                )
            } else {
                Text("$ unlock", style = PassType.Body.copy(color = PassColorsDark.Accent))
            }
        }
    }
}
