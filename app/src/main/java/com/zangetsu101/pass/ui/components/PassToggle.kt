// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.theme.PassColorsDark

@Composable
fun PassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) PassColorsDark.Accent else PassColorsDark.Border2,
        animationSpec = tween(150),
        label = "track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 4.dp,
        animationSpec = tween(150),
        label = "thumb",
    )

    Box(
        modifier =
            modifier
                .size(width = 32.dp, height = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(trackColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onCheckedChange(!checked) },
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (checked) PassColorsDark.Border2 else Color.White),
        )
    }
}
