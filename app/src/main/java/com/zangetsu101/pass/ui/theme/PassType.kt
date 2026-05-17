package com.zangetsu101.pass.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zangetsu101.pass.R

val JetBrainsMono =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        Font(R.font.jetbrains_mono_light, FontWeight.Light),
    )

object PassType {
    val Title =
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = PassColorsDark.Accent,
        )
    val Body =
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = PassColorsDark.TextPrimary,
        )
    val Label =
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.12.em,
            color = PassColorsDark.Accent,
        )
    val Caption =
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Normal,
            fontSize = 9.sp,
            color = PassColorsDark.TextDim,
        )
    val Display =
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = (-0.02).em,
            color = PassColorsDark.Accent,
        )
}
