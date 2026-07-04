// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassType

@Composable
fun PassPrimaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = PassColorsDark.AccentDim,
                contentColor = PassColorsDark.Accent,
                disabledContainerColor = PassColorsDark.Border,
                disabledContentColor = PassColorsDark.TextFaint,
            ),
        border = BorderStroke(1.dp, if (enabled) PassColorsDark.Accent else PassColorsDark.Border),
        modifier = modifier.fillMaxWidth().height(40.dp),
    ) {
        Text(label, style = PassType.Body.copy(color = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint))
    }
}

@Composable
fun PassSecondaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = PassColorsDark.TextDim,
                disabledContentColor = PassColorsDark.TextFaint,
            ),
        border = BorderStroke(1.dp, if (enabled) PassColorsDark.Border2 else PassColorsDark.Border),
        modifier = modifier.fillMaxWidth().height(40.dp),
    ) {
        Text(label, style = PassType.Body.copy(color = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint))
    }
}
