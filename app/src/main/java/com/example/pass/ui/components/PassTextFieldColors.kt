package com.example.pass.ui.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.example.pass.ui.theme.PassColorsDark

@Composable
fun passTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = PassColorsDark.Accent,
    unfocusedBorderColor = PassColorsDark.Border2,
    focusedLabelColor    = PassColorsDark.Accent,
    unfocusedLabelColor  = PassColorsDark.TextDim,
    cursorColor          = PassColorsDark.Accent,
    focusedTextColor     = PassColorsDark.TextPrimary,
    unfocusedTextColor   = PassColorsDark.TextPrimary,
    errorBorderColor     = PassColorsDark.Danger,
    errorLabelColor      = PassColorsDark.Danger,
    errorCursorColor     = PassColorsDark.Danger,
)
