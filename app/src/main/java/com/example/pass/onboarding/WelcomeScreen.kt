package com.example.pass.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.pass.R
import com.example.pass.ui.components.PassPrimaryButton
import com.example.pass.ui.components.PassScaffold
import com.example.pass.ui.theme.PassColorsDark
import com.example.pass.ui.theme.PassType

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    PassScaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(top = 40.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .background(PassColorsDark.AccentDim, RoundedCornerShape(8.dp))
                            .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = PassColorsDark.Accent,
                        modifier =
                            Modifier.size(52.dp).graphicsLayer {
                                scaleX = 1.6f
                                scaleY = 1.6f
                            },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text =
                        buildAnnotatedString {
                            append("pass")
                            withStyle(SpanStyle(color = PassColorsDark.TextDim, fontWeight = FontWeight.Light)) {
                                append(".android")
                            }
                        },
                    style = PassType.Display,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "the standard unix password manager — on your phone",
                    style = PassType.Body,
                )
                Spacer(Modifier.height(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow("gpg", "end-to-end encrypted with your gpg key")
                    FeatureRow("git", "syncs with any git remote")
                    FeatureRow("pass", "100% compatible with unix pass")
                }
            }
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                PassPrimaryButton(onClick = onStart, label = "$ clone a store")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "requires git + gpg key",
                    style = PassType.Caption,
                    color = PassColorsDark.TextFaint,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    tag: String,
    description: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier =
                Modifier
                    .background(PassColorsDark.AccentDim, RoundedCornerShape(3.dp))
                    .border(1.dp, PassColorsDark.AccentMid, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(tag, style = PassType.Label)
        }
        Text(description, style = PassType.Body)
    }
}
