package com.fuelexpenselog.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.Note
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.common.SunkenPanel
import com.fuelexpenselog.app.ui.common.UnderlineField
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.common.longLabel
import com.fuelexpenselog.app.ui.common.topHairline
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    onImport: (uri: String, vehicleId: Long) -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val handoff by viewModel.handoff.collectAsStateWithLifecycle()
    LaunchedEffect(state.done, handoff) {
        if (state.done) handoff?.let { onImport(it.uri, it.vehicleId) } ?: onDone()
    }
    BackHandler(enabled = state.step != OnboardingStep.COUNTRY) { viewModel.back() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.finish(importUri = it.toString()) }
    }

    when (state.step) {
        OnboardingStep.COUNTRY -> CountryScreen(state, viewModel::onCountry, viewModel::next)
        OnboardingStep.UNITS -> UnitsScreen(state, viewModel::onPreset, viewModel::next)
        OnboardingStep.VEHICLE -> FirstVehicleScreen(
            state = state,
            onName = viewModel::onName,
            onStart = { viewModel.finish() },
            onImport = { picker.launch(arrayOf("*/*")) },
        )
    }
}

/** Eyebrow, title and an optional line of explanation: the top of every first-run step. */
@Composable
internal fun ColumnScope.StepHeading(eyebrow: String, title: String, body: String? = null, top: Int = 34) {
    Column(
        Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = top.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(eyebrow)
        Text(
            title,
            style = FuelTheme.type.title,
            color = FuelTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) Text(body, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary)
    }
}

/** 02: where the user drives, which sets units and currency together. */
@Composable
fun CountryScreen(state: OnboardingUiState, onCountry: (String) -> Unit, onContinue: () -> Unit) {
    val f = LocalFormatters.current
    var query by rememberSaveable { mutableStateOf("") }
    FuelScreen(Modifier.imePadding()) {
        StepHeading(
            eyebrow = stringResource(R.string.onboarding_eyebrow_local),
            title = stringResource(R.string.onboarding_country_title),
            body = stringResource(R.string.onboarding_country_body),
        )
        CountryList(
            query = query,
            onQuery = { query = it },
            selectedCode = state.region.countryCode,
            onSelect = onCountry,
            modifier = Modifier.weight(1f),
        )
        SunkenPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_selected),
                    style = FuelTheme.type.meta,
                    color = FuelTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                val name = countryName(state.region.countryCode, f.locale).ifEmpty { stringResource(R.string.country_no_name) }
                Text(
                    listOf(name, "${state.region.currencyCode} ${f.currency.symbol(state.region.currencyCode)}", state.region.distanceUnit.longLabel())
                        .joinToString(" · "),
                    style = FuelTheme.type.body,
                    color = FuelTheme.colors.textPrimary,
                )
            }
        }
        PrimaryButton(
            stringResource(R.string.onboarding_continue),
            onContinue,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 22.dp),
        )
    }
}

/** 03: confirm or override what the region implies. */
@Composable
fun UnitsScreen(state: OnboardingUiState, onPreset: (UnitPreset) -> Unit, onContinue: () -> Unit) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val type = FuelTheme.type
    val preset = state.preset
    val sameAsPhone = state.region.countryCode == state.detected.countryCode

    FuelScreen {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StepHeading(stringResource(R.string.onboarding_eyebrow_local), stringResource(R.string.onboarding_units_title), top = 40)

            Column(
                Modifier
                    .padding(horizontal = Dimens.gutter)
                    .border(Dimens.hairline, colors.textPrimary),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.primary)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val header = if (sameAsPhone) {
                        stringResource(R.string.onboarding_detected, state.localeTag)
                    } else {
                        stringResource(R.string.onboarding_chosen, countryName(state.region.countryCode, f.locale))
                    }
                    Text(
                        header.toUpperCase(Locale.current),
                        style = type.eyebrow,
                        color = colors.onPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (sameAsPhone) {
                        Text(stringResource(R.string.onboarding_device_setting), style = type.meta, color = colors.onPrimary)
                    }
                }
                DetectedRow(
                    stringResource(R.string.onboarding_row_region),
                    countryName(state.region.countryCode, f.locale).ifEmpty { stringResource(R.string.country_no_name) },
                )
                DetectedRow(stringResource(R.string.onboarding_row_distance), preset.distance.longLabel())
                DetectedRow(stringResource(R.string.onboarding_row_volume), preset.volume.longLabel())
                DetectedRow(
                    stringResource(R.string.onboarding_row_currency),
                    "${state.region.currencyCode} ${f.currency.symbol(state.region.currencyCode)}",
                )
            }

            Column(
                Modifier
                    .padding(start = Dimens.gutter, end = Dimens.gutter, top = 26.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel(stringResource(R.string.onboarding_other_set))
                UnitPreset.entries.forEach { option ->
                    PresetRow(option, selected = option == preset, format = option.format(state.region).label()) {
                        onPreset(option)
                    }
                }
                Text(
                    stringResource(R.string.onboarding_units_footnote),
                    style = type.meta,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                )
            }
        }
        PrimaryButton(
            stringResource(R.string.onboarding_units_continue),
            onContinue,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun DetectedRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .topHairline(FuelTheme.colors.outline)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = FuelTheme.type.meta, color = FuelTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = FuelTheme.type.body, color = FuelTheme.colors.textPrimary)
    }
}

@Composable
private fun PresetRow(preset: UnitPreset, selected: Boolean, format: String, onClick: () -> Unit) {
    val colors = FuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .border(Dimens.hairline, if (selected) colors.textPrimary else colors.outlineStrong)
            .background(if (selected) colors.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(preset.label), style = FuelTheme.type.body, color = if (selected) colors.onPrimary else colors.textPrimary)
            Text(
                "${preset.distance.longLabel()} · ${preset.volume.longLabel()}",
                style = FuelTheme.type.meta,
                color = if (selected) colors.onPrimarySecondary else colors.textSecondary,
            )
        }
        Text(
            format.toUpperCase(Locale.current),
            style = FuelTheme.type.eyebrow,
            color = if (selected) colors.onPrimary else colors.textSecondary,
        )
    }
}

/** 04: the first vehicle, and then out of the way. No tutorial and no account step. */
@Composable
fun FirstVehicleScreen(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onStart: () -> Unit,
    onImport: () -> Unit = {},
) {
    val f = LocalFormatters.current
    FuelScreen(Modifier.imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StepHeading(
                stringResource(R.string.onboarding_vehicle_eyebrow),
                stringResource(R.string.onboarding_vehicle_title),
                top = 44,
            )
            Column(
                Modifier.padding(horizontal = Dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                UnderlineField(
                    value = state.name,
                    onValueChange = onName,
                    label = stringResource(R.string.onboarding_vehicle_name),
                    placeholder = stringResource(R.string.editor_name_placeholder),
                    textStyle = FuelTheme.type.figureRow,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    supporting = stringResource(
                        R.string.preset_summary,
                        state.preset.distance.longLabel(),
                        state.preset.volume.longLabel(),
                        "${state.region.currencyCode} ${f.currency.symbol(state.region.currencyCode)}",
                    ),
                )
                Note(stringResource(R.string.onboarding_privacy))
            }
        }
        ImportLink(enabled = state.canFinish, onClick = onImport)
        PrimaryButton(
            stringResource(R.string.onboarding_start),
            onStart,
            enabled = state.canFinish,
            modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 12.dp, bottom = 22.dp),
        )
    }
}

/**
 * "Already logging elsewhere? Import a CSV": an inline link with the handoff's yellow 2dp
 * underline under ink text. Like Start logging, it needs the vehicle's name first, since that
 * vehicle is where the history goes.
 */
@Composable
private fun ImportLink(enabled: Boolean, onClick: () -> Unit) {
    val colors = FuelTheme.colors
    val underline = colors.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(stringResource(R.string.onboarding_import_prompt), style = FuelTheme.type.meta, color = colors.textSecondary)
        Text(
            stringResource(R.string.onboarding_import_link),
            style = FuelTheme.type.meta.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.drawBehind {
                if (enabled) {
                    val stroke = 2.dp.toPx()
                    drawRect(underline, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
                }
            },
        )
    }
}
