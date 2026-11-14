// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
fun PassTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = PassType.Caption.copy(color = PassColorsDark.Accent),
        modifier =
            modifier
                .clip(PassShapes.extraSmall)
                .background(PassColorsDark.AccentDim)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
