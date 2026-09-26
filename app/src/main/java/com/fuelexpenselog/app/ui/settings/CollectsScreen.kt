package com.fuelexpenselog.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.SectionLabel
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

/**
 * "What this app collects", in plain words. It says Android's own backup exists, because it
 * does: the honest claim is no internet permission, no account, no ads, no analytics - never
 * that nothing can leave the device.
 */
@Composable
fun CollectsScreen(onBack: () -> Unit) {
    val colors = FuelTheme.colors
    FuelScreen {
        NavHeader(stringResource(R.string.collects_title), onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(stringResource(R.string.collects_lead), style = FuelTheme.type.subtitle, color = colors.textPrimary)
            Section(R.string.collects_stored_heading, R.string.collects_stored_body)
            Section(R.string.collects_backup_heading, R.string.collects_backup_body)
            Section(R.string.collects_permissions_heading, R.string.collects_permissions_body)
            Section(R.string.collects_not_heading, R.string.collects_not_body)
            Section(R.string.collects_delete_heading, R.string.collects_delete_body)
            Section(R.string.collects_licences_heading, R.string.collects_licences_body)
        }
    }
}

@Composable
private fun Section(heading: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.labelToContent)) {
        SectionLabel(stringResource(heading), Modifier.semantics { heading() })
        Text(stringResource(body), style = FuelTheme.type.bodyRegular, color = FuelTheme.colors.textPrimary)
    }
}
