// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zangetsu101.pass.generator.ALL_SYMBOLS
import com.zangetsu101.pass.generator.CharacterSet
import com.zangetsu101.pass.generator.DEFAULT_LENGTH
import com.zangetsu101.pass.generator.GeneratorSettings
import com.zangetsu101.pass.generator.MAX_LENGTH
import com.zangetsu101.pass.generator.MIN_LENGTH
import com.zangetsu101.pass.generator.SAFE_SYMBOLS
import com.zangetsu101.pass.generator.Strength
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

/** What the label row of the password card says about the password. */
internal enum class PasswordStatus { Generated, Typed }

private val captionSmall = PassType.Caption.copy(fontSize = 9.sp)

// JetBrains Mono joins character pairs such as `<=` into one glyph. A password must show each character.
private const val NO_LIGATURES = "\"calt\" 0, \"liga\" 0"
private val subLabel = PassType.Caption.copy(fontSize = 9.sp, letterSpacing = 0.1.em)

@Composable
internal fun PasswordCard(
    password: String,
    status: PasswordStatus?,
    strength: Strength,
    enabled: Boolean,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    glow: Boolean = false,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("password", style = PassType.Label, modifier = Modifier.weight(1f))
        when (status) {
            PasswordStatus.Generated -> Text("auto-generated · tap to edit", style = captionSmall)
            PasswordStatus.Typed -> Text("edited by you", style = captionSmall.copy(color = PassColorsDark.Accent))
            null -> Unit
        }
    }
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (glow) {
                        Modifier.dropShadow(PassShapes.small, Shadow(radius = 20.dp, color = PassColorsDark.AccentDim))
                    } else {
                        Modifier
                    },
                ).background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Accent.copy(alpha = 0.33f), PassShapes.small)
                .clip(PassShapes.small),
    ) {
        BasicTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            singleLine = false,
            textStyle =
                PassType.Body.copy(
                    color = if (revealed) PassColorsDark.TextPrimary else PassColorsDark.TextDim,
                    letterSpacing = 0.04.em,
                    fontFeatureSettings = NO_LIGATURES,
                ),
            cursorBrush = SolidColor(PassColorsDark.Accent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            visualTransformation = if (revealed) CharacterClassColors else PasswordVisualTransformation(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
                    .semantics { contentDescription = "edit password" },
        )
        StrengthBar(strength)
        HorizontalDivider(color = PassColorsDark.Border, thickness = 1.dp)
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            CardAction(label = "↺ regenerate", enabled = enabled, onClick = onRegenerate, weight = 2f, primary = true)
            VerticalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            CardAction(label = if (revealed) "hide" else "show", enabled = true, onClick = onToggleReveal)
            VerticalDivider(color = PassColorsDark.Border, thickness = 1.dp)
            CardAction(label = "copy", enabled = password.isNotEmpty(), onClick = onCopy)
        }
    }
}

@Composable
private fun StrengthBar(strength: Strength) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        if (index < strength.level.segments) PassColorsDark.Accent else PassColorsDark.Border2,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
        Spacer(Modifier.width(3.dp))
        Text(strength.label, style = captionSmall, maxLines = 1)
    }
}

@Composable
private fun RowScope.CardAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    weight: Float = 1f,
    primary: Boolean = false,
) {
    val color =
        when {
            !enabled -> PassColorsDark.TextFaint
            primary -> PassColorsDark.Accent
            else -> PassColorsDark.TextDim
        }
    Box(
        modifier =
            Modifier
                .weight(weight)
                .fillMaxHeight()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style =
                PassType.Caption.copy(
                    fontSize = 10.sp,
                    color = color,
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}

/** The inline generator settings. Each change makes a new password at once. */
@Composable
internal fun CustomizePanel(
    settings: GeneratorSettings,
    open: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onChange: (GeneratorSettings) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (open) PassColorsDark.Border2 else PassColorsDark.Border, PassShapes.small)
            .background(if (open) PassColorsDark.Raised else Color.Transparent, PassShapes.small),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onToggleOpen)
                    .semantics { stateDescription = if (open) "open" else "closed" }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SlidersIcon()
            Text("customize", style = PassType.Body.copy(fontSize = 10.sp))
            Text(
                settings.summary,
                style = captionSmall.copy(color = if (settings.isDefault) PassColorsDark.TextDim else PassColorsDark.Accent),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            ChevronIcon(Modifier.rotate(if (open) 90f else 0f))
        }
        if (open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp)) {
                LengthControls(settings, enabled, onChange)
                Spacer(Modifier.height(12.dp))
                Text("characters", style = subLabel)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (set in CharacterSet.entries) {
                        Chip(
                            label = set.label,
                            active = set in settings.sets,
                            enabled = enabled,
                            onClick = { onChange(settings.toggle(set)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (settings.symbolsOn) {
                    Spacer(Modifier.height(10.dp))
                    SymbolControls(settings, enabled, onChange)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(settings.note, style = captionSmall, modifier = Modifier.weight(1f))
                    if (!settings.isDefault) {
                        Text(
                            "reset",
                            style = captionSmall.copy(color = PassColorsDark.Accent),
                            modifier =
                                Modifier
                                    .clickable(enabled = enabled, role = Role.Button) { onChange(GeneratorSettings()) }
                                    .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

private val lengthPresets = listOf(16, 20, 25, 32, 64)

@Composable
private fun LengthControls(
    settings: GeneratorSettings,
    enabled: Boolean,
    onChange: (GeneratorSettings) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("length", style = subLabel, modifier = Modifier.weight(1f))
        StepButton("−", "shorter", enabled && settings.length > MIN_LENGTH) { onChange(settings.withLength(settings.length - 1)) }
        Text(
            settings.length.toString(),
            style = PassType.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PassColorsDark.Accent),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        StepButton("+", "longer", enabled && settings.length < MAX_LENGTH) { onChange(settings.withLength(settings.length + 1)) }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (length in lengthPresets) {
            Chip(
                label = if (length == DEFAULT_LENGTH) "$length*" else "$length",
                active = settings.length == length,
                enabled = enabled,
                onClick = { onChange(settings.withLength(length)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SymbolControls(
    settings: GeneratorSettings,
    enabled: Boolean,
    onChange: (GeneratorSettings) -> Unit,
) {
    val custom = settings.customSymbols
    Row(verticalAlignment = Alignment.Bottom) {
        Text("allowed symbols", style = subLabel, modifier = Modifier.weight(1f))
        Text(
            "${settings.symbols.length} of ${ALL_SYMBOLS.length}",
            style = captionSmall.copy(color = if (custom) PassColorsDark.Accent else PassColorsDark.TextDim),
        )
    }
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = settings.symbols,
        onValueChange = { onChange(settings.withSymbols(it)) },
        enabled = enabled,
        singleLine = true,
        textStyle =
            PassType.Body.copy(
                fontSize = 11.sp,
                letterSpacing = 0.12.em,
                color = PassColorsDark.Warning,
                fontFeatureSettings = NO_LIGATURES,
            ),
        cursorBrush = SolidColor(PassColorsDark.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PassColorsDark.Surface, PassShapes.extraSmall)
                .border(1.dp, if (custom) PassColorsDark.AccentMid else PassColorsDark.Border2, PassShapes.extraSmall)
                .padding(horizontal = 9.dp, vertical = 7.dp)
                .semantics { contentDescription = "allowed symbols" },
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, symbols) in listOf("all" to ALL_SYMBOLS, "safe  $SAFE_SYMBOLS" to SAFE_SYMBOLS, "-_." to "-_.")) {
            Chip(
                label = label,
                active = settings.symbols == symbols,
                enabled = enabled,
                onClick = { onChange(settings.withSymbols(symbols)) },
                fontSize = 9.sp,
                horizontalPadding = 8.dp,
            )
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .border(1.dp, PassColorsDark.Border2, PassShapes.extraSmall)
                .clip(PassShapes.extraSmall)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PassType.Caption.copy(fontSize = 12.sp, color = if (enabled) PassColorsDark.TextDim else PassColorsDark.TextFaint),
        )
    }
}

@Composable
private fun Chip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    horizontalPadding: Dp = 0.dp,
) {
    Box(
        modifier =
            modifier
                .background(if (active) PassColorsDark.AccentDim else PassColorsDark.Surface, PassShapes.extraSmall)
                .border(1.dp, if (active) PassColorsDark.Accent else PassColorsDark.Border2, PassShapes.extraSmall)
                .clip(PassShapes.extraSmall)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { stateDescription = if (active) "on" else "off" }
                .padding(horizontal = horizontalPadding, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style =
                PassType.Caption.copy(
                    fontSize = fontSize,
                    color = if (active) PassColorsDark.Accent else PassColorsDark.TextDim,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun CopyAfterSaveRow(
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    contentDescription = "copy after saving"
                    stateDescription = if (on) "on" else "off"
                }.padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append("copy after saving ")
                withStyle(SpanStyle(color = PassColorsDark.TextDim)) { append("-c") }
            },
            style = PassType.Body.copy(fontSize = 10.sp),
            modifier = Modifier.weight(1f),
        )
        PassToggle(on)
    }
}

/** A custom toggle, because the Material3 Switch does not match the terminal style of the app. */
@Composable
private fun PassToggle(on: Boolean) {
    Box(
        Modifier
            .size(width = 30.dp, height = 16.dp)
            .background(if (on) PassColorsDark.AccentMid else PassColorsDark.Border, CircleShape)
            .border(1.dp, if (on) PassColorsDark.Accent else PassColorsDark.Border2, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = if (on) 15.dp else 2.dp, y = 2.dp)
                .size(10.dp)
                .background(if (on) PassColorsDark.Accent else PassColorsDark.TextDim, CircleShape),
        )
    }
}

@Composable
private fun SlidersIcon() {
    Canvas(Modifier.size(12.dp)) {
        val unit = size.width / 12
        val stroke = 1.2f * unit
        val color = PassColorsDark.TextDim

        fun line(
            x1: Float,
            y: Float,
            x2: Float,
        ) = drawLine(color, Offset(x1 * unit, y * unit), Offset(x2 * unit, y * unit), stroke, StrokeCap.Round)
        line(1f, 3f, 7f)
        line(9f, 3f, 11f)
        line(1f, 9f, 3f)
        line(5f, 9f, 11f)
        drawCircle(color, 1.4f * unit, Offset(8f * unit, 3f * unit), style = Stroke(stroke))
        drawCircle(color, 1.4f * unit, Offset(4f * unit, 9f * unit), style = Stroke(stroke))
    }
}

@Composable
private fun ChevronIcon(modifier: Modifier) {
    Canvas(modifier.size(10.dp)) {
        val unit = size.width / 10
        val stroke = 1.3f * unit
        val color = PassColorsDark.TextDim
        drawLine(color, Offset(4f * unit, 2f * unit), Offset(7f * unit, 5f * unit), stroke, StrokeCap.Round)
        drawLine(color, Offset(7f * unit, 5f * unit), Offset(4f * unit, 8f * unit), stroke, StrokeCap.Round)
    }
}

/** Colors digits with the accent and symbols with the warning color, as the new-entry design does. */
private object CharacterClassColors : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val colored =
            buildAnnotatedString {
                text.text.forEach { char ->
                    val color: Color? =
                        when {
                            char.isDigit() -> PassColorsDark.Accent
                            char.isLetter() -> null
                            else -> PassColorsDark.Warning
                        }
                    if (color == null) append(char) else withStyle(SpanStyle(color = color)) { append(char) }
                }
            }
        return TransformedText(colored, OffsetMapping.Identity)
    }
}
