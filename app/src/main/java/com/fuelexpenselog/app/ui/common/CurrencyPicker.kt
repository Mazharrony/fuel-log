package com.fuelexpenselog.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import java.util.Currency

/**
 * Every currency the platform knows, searchable by code or name. The list comes from the
 * phone's own ICU data - there is no network to fetch a fresher one from, and no need.
 */
@Composable
fun CurrencyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val formatters = LocalFormatters.current
    var query by rememberSaveable { mutableStateOf("") }

    val all = remember(formatters) {
        Currency.getAvailableCurrencies()
            .map { it.currencyCode to formatters.currency.displayName(it.currencyCode) }
            .sortedBy { it.first }
    }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { (code, name) -> code.contains(q, ignoreCase = true) || name.contains(q, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .fillMaxWidth()
                .background(colors.background),
        ) {
            NavHeader(title = stringResource(R.string.currency_picker_title), onBack = onDismiss)
            UnderlineField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.currency_search),
                textStyle = type.body,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search,
                modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 12.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                items(shown, key = { it.first }) { (code, name) ->
                    val isSelected = code == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .background(if (isSelected) colors.primary else Color.Transparent)
                            .topHairline(colors.outline)
                            .clickable(role = Role.Button) { onSelect(code) }
                            .padding(horizontal = Dimens.gutter, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(code, style = type.body, color = if (isSelected) colors.onPrimary else colors.textPrimary)
                        Text(
                            name,
                            style = type.meta,
                            color = if (isSelected) colors.onPrimarySecondary else colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatters.currency.symbol(code),
                            style = type.meta,
                            color = if (isSelected) colors.onPrimary else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** A dropdown-looking field that opens [CurrencyPickerDialog]. */
@Composable
fun CurrencyField(
    code: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FuelTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.editor_currency)

    // The same stack as UnderlineField - label, value, 7dp, 1dp rule - so the two underlines
    // line up when the fields sit side by side. The whole stack is the touch target.
    Column(
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClickLabel = label) { open = true },
        verticalArrangement = Arrangement.spacedBy(Dimens.labelToContent),
    ) {
        SectionLabel(label)
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    code,
                    style = FuelTheme.type.subtitle,
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                FuelIcon(R.drawable.ic_chevron_down, null, tint = colors.textSecondary)
            }
            Hairline(color = colors.outlineStrong)
        }
    }

    if (open) {
        CurrencyPickerDialog(
            selected = code,
            onSelect = {
                onChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}
