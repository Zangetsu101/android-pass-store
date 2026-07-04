// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.R
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes

@Composable
fun PassAppIcon(
    size: Dp,
    showGlow: Boolean = false,
) {
    val glowRadius = size * 2f
    Box(
        modifier =
            Modifier
                .then(
                    if (showGlow) {
                        Modifier.drawBehind {
                            drawCircle(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(PassColorsDark.AccentDim, Color.Transparent),
                                        radius = glowRadius.toPx(),
                                    ),
                                radius = glowRadius.toPx(),
                            )
                        }
                    } else {
                        Modifier
                    },
                ).size(size)
                .background(PassColorsDark.AccentDim, PassShapes.large)
                .border(1.dp, PassColorsDark.AccentMid, PassShapes.large)
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            tint = PassColorsDark.Accent,
            modifier =
                Modifier.size(size).graphicsLayer {
                    scaleX = 1.4f
                    scaleY = 1.4f
                },
        )
    }
}
