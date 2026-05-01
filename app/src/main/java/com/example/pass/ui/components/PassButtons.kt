package com.example.pass.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@Composable
fun PassPrimaryButton(
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
            containerColor = PassColorsDark.AccentDim,
            contentColor = PassColorsDark.Accent,
            disabledContainerColor = PassColorsDark.Border,
            disabledContentColor = PassColorsDark.TextFaint,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, if (enabled) PassColorsDark.Accent else PassColorsDark.Border, MaterialTheme.shapes.extraSmall),
    ) {
        Text(label, style = PassType.Body.copy(color = if (enabled) PassColorsDark.Accent else PassColorsDark.TextFaint))
    }
}

@Composable
fun PassSecondaryButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PassColorsDark.TextDim),
        border = BorderStroke(1.dp, PassColorsDark.Border2),
        modifier = modifier.fillMaxWidth().height(40.dp),
    ) {
        Text(label, style = PassType.Body.copy(color = PassColorsDark.TextDim))
    }
}
