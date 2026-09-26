package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * An input with no box: label above, the value at figure scale, an underline below - 2dp ink
 * while focused, 1dp grey at rest. The caret is the handoff's 2dp yellow bar; drawing it as
 * the real cursor keeps it at the insertion point instead of pinned to the end of the text.
 *
 * Numeric fields take `KeyboardType.Decimal` and hand the raw text to DecimalParser. Nothing
 * here parses or reformats what the user typed.
 */
@Composable
fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    textStyle: TextStyle = FuelTheme.type.figureInput,
    placeholder: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    var focused by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.labelToContent)) {
        if (label != null) SectionLabel(label)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle.copy(color = if (enabled) colors.textPrimary else colors.textSecondary),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization,
                autoCorrectEnabled = keyboardType == KeyboardType.Text,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() ?: defaultKeyboardAction(imeAction) }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (label != null) contentDescription = label },
            decorationBox = { inner ->
                Column {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 7.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = textStyle, color = colors.outlineStrong, maxLines = 1)
                            }
                            inner()
                        }
                        if (suffix != null) {
                            Text(
                                suffix,
                                style = type.meta,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    Hairline(
                        color = if (focused) colors.textPrimary else colors.outlineStrong,
                        modifier = Modifier,
                        thickness = if (focused) Dimens.underlineFocused else Dimens.underlineResting,
                    )
                }
            },
        )

        if (supporting != null) {
            Text(supporting, style = type.meta, color = colors.textSecondary)
        }
    }
}
