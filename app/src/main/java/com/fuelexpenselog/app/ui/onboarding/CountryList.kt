package com.fuelexpenselog.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.Formatters
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelIcon
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.region.RegionDefaults
import com.fuelexpenselog.domain.region.Regions
import java.util.Locale

data class CountryRow(val region: RegionDefaults, val name: String)

/** A country's name in the user's language, or a neutral label when the phone names none. */
fun countryName(code: String, locale: Locale): String {
    if (code.isEmpty()) return ""
    @Suppress("DEPRECATION")
    return Locale("", code).getDisplayCountry(locale).ifEmpty { code }
}

/** The six seed markets first, then everyone else alphabetically in the user's language. */
@Composable
fun rememberCountries(locale: Locale): List<CountryRow> = remember(locale) {
    val seed = Regions.seed.map { CountryRow(it, countryName(it.countryCode, locale)) }
    val rest = Regions.all
        .filter { it.countryCode !in Regions.seedCodes }
        .map { CountryRow(it, countryName(it.countryCode, locale)) }
        .sortedBy { it.name.lowercase(locale) }
    seed + rest
}

/** "mi · US gal · MPG (US)": what a region implies, in the short labels. */
fun unitsLine(region: RegionDefaults, f: Formatters, format: String): String =
    listOf(f.distance.unitLabel(region.distanceUnit), f.volume.unitLabel(region.volumeUnit), format).joinToString(" · ")

@Composable
fun CountryList(
    query: String,
    onQuery: (String) -> Unit,
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val all = rememberCountries(f.locale)
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) || it.region.countryCode.equals(q, ignoreCase = true) }
    }
    val searchLabel = stringResource(R.string.onboarding_country_search)

    Column(modifier) {
        Row(
            modifier = Modifier
                .padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 14.dp)
                .fillMaxWidth()
                .border(Dimens.hairline, colors.outlineStrong)
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FuelIcon(R.drawable.ic_search, null, tint = colors.textSecondary)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(searchLabel, style = type.bodyRegular, color = colors.textSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = type.bodyRegular.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchLabel },
                )
            }
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { it.region.countryCode }) { row ->
                val selected = row.region.countryCode == selectedCode
                val format = stringResource(com.fuelexpenselog.app.format.ConsumptionFormatter.labelRes(row.region.consumptionFormat))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .background(if (selected) colors.primary else Color.Transparent)
                        .topHairline(colors.outline)
                        .clickable(role = Role.RadioButton) { onSelect(row.region.countryCode) }
                        .padding(horizontal = Dimens.gutter, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.name, style = type.body, color = if (selected) colors.onPrimary else colors.textPrimary)
                        Text(
                            unitsLine(row.region, f, format),
                            style = type.meta,
                            color = if (selected) colors.onPrimarySecondary else colors.textSecondary,
                        )
                    }
                    Text(
                        row.region.currencyCode,
                        style = type.body,
                        color = if (selected) colors.onPrimary else colors.textPrimary,
                    )
                }
            }
        }
    }
}
