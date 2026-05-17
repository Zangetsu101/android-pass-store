package com.zangetsu101.pass.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.zangetsu101.pass.R
import com.zangetsu101.pass.ui.components.PassAppIcon
import com.zangetsu101.pass.ui.theme.JetBrainsMono
import com.zangetsu101.pass.ui.theme.PassColorsDark

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val barFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 1800
                        0f at 0 using FastOutSlowInEasing
                        1f at 1080
                        1f at 1800
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "bar",
    )

    val barWidthPx = with(LocalDensity.current) { 72.dp.toPx() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PassColorsDark.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PassAppIcon(size = 64.dp, showGlow = true)

            Spacer(Modifier.height(20.dp))

            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = PassColorsDark.Accent, fontWeight = FontWeight.Bold)) {
                            append("pass")
                        }
                        withStyle(SpanStyle(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light)) {
                            append(".android")
                        }
                    },
                fontSize = 26.sp,
                letterSpacing = (-0.02).em,
                fontFamily = JetBrainsMono,
                lineHeight = 26.sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "the unix password manager",
                fontSize = 10.sp,
                letterSpacing = 0.15.em,
                color = PassColorsDark.TextFaint,
                fontFamily = JetBrainsMono,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(120.dp)
                        .height(2.dp)
                        .background(PassColorsDark.Border2, RoundedCornerShape(2.dp))
                        .clipToBounds(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(72.dp)
                            .graphicsLayer {
                                translationX = lerp(-barWidthPx, 2f * barWidthPx, barFraction)
                            }.background(
                                Brush.horizontalGradient(
                                    listOf(PassColorsDark.AccentMid, PassColorsDark.Accent),
                                ),
                                RoundedCornerShape(2.dp),
                            ),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "initialising…",
                fontSize = 9.sp,
                color = PassColorsDark.TextFaint,
                fontFamily = JetBrainsMono,
            )
        }
    }
}
