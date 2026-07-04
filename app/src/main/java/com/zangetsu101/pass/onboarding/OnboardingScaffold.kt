// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zangetsu101.pass.ui.components.PassScaffold
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

@Composable
internal fun OnboardingScaffold(
    step: Int,
    total: Int,
    title: String,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    PassScaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "SETUP · $step / $total", style = PassType.Caption)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(total) { i ->
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 20.dp, height = 3.dp)
                                    .background(
                                        PassColorsDark.Accent.copy(alpha = if (i + 1 == step) 1f else 0.3f),
                                        PassShapes.small,
                                    ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = PassType.Title)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = PassType.Caption, lineHeight = PassType.Caption.fontSize * 1.6)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
