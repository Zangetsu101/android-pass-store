// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zangetsu101.pass.ui.theme.PassColorsDark
import com.zangetsu101.pass.ui.theme.PassShapes
import com.zangetsu101.pass.ui.theme.PassType

private val captionSmall = PassType.Caption.copy(fontSize = 9.sp)
private val numberStyle = PassType.Body.copy(fontSize = 15.sp, letterSpacing = 0.06.em)

/** The `login | card` toggle in the app bar of the new-entry form. */
@Composable
internal fun EntryTypeToggle(
    type: EntryType,
    enabled: Boolean,
    onSelect: (EntryType) -> Unit,
) {
    Row(
        Modifier
            .clip(PassShapes.extraSmall)
            .border(1.dp, PassColorsDark.Border2, PassShapes.extraSmall),
    ) {
        EntryType.entries.forEach { option ->
            val active = option == type
            Text(
                option.name.lowercase(),
                style =
                    captionSmall.copy(
                        color = if (active) PassColorsDark.Accent else PassColorsDark.TextDim,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                modifier =
                    Modifier
                        .background(if (active) PassColorsDark.AccentDim else Color.Transparent)
                        .selectable(selected = active, enabled = enabled, role = Role.Tab) { onSelect(option) }
                        .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

/** Line 1 of a Card Entry. The field holds digits only and shows them in groups. */
@Composable
internal fun CardNumberField(
    digits: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("card number", style = PassType.Label, modifier = Modifier.weight(1f))
        Text("line 1 · in place of password", style = captionSmall)
    }
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = digits,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        textStyle = numberStyle,
        cursorBrush = SolidColor(PassColorsDark.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrectEnabled = false),
        visualTransformation = CardNumberGroups,
        modifier =
            Modifier
                .fillMaxWidth()
                .dropShadow(PassShapes.small, Shadow(radius = 20.dp, color = PassColorsDark.AccentDim))
                .background(PassColorsDark.Surface, PassShapes.small)
                .border(1.dp, PassColorsDark.Accent.copy(alpha = 0.33f), PassShapes.small)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .semantics { contentDescription = "edit card number" },
        decorationBox = { field ->
            Box {
                if (digits.isEmpty()) {
                    Text(
                        "0000 0000 0000 0000",
                        style = numberStyle.copy(color = PassColorsDark.TextFaint),
                    )
                }
                field()
            }
        },
    )
    Spacer(Modifier.height(6.dp))
    Text(
        cardNumberStatus(digits),
        style = captionSmall.copy(color = if (cardChecksumFails(digits)) PassColorsDark.Warning else PassColorsDark.TextDim),
    )
}

private object CardNumberGroups : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = groupCardNumber(text.text)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    if (offset == 0) return 0
                    var digits = 0
                    grouped.forEachIndexed { index, char ->
                        if (char != ' ' && ++digits == offset) return index + 1
                    }
                    return grouped.length
                }

                override fun transformedToOriginal(offset: Int) = grouped.take(offset).count { it != ' ' }
            }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** The warning below the path of a card that is outside a Card Folder. The save is allowed. */
@Composable
internal fun CardFolderWarning(
    enabled: Boolean,
    onAddCardFolder: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .border(1.dp, PassColorsDark.Warning, PassShapes.extraSmall)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "no cards/ folder · this saves as a login: no card view, no card autofill",
            style = captionSmall.copy(color = PassColorsDark.Warning, lineHeight = 1.5.em),
            modifier = Modifier.weight(1f),
        )
        Text(
            "add cards/",
            style = captionSmall.copy(color = PassColorsDark.Warning, fontWeight = FontWeight.SemiBold),
            modifier =
                Modifier
                    .clip(PassShapes.extraSmall)
                    .border(1.dp, PassColorsDark.Warning, PassShapes.extraSmall)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onAddCardFolder)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
