package com.fuelexpenselog.app.ui.reminders

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.Hairline
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.PrimaryButton
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme

@Composable
fun RemindersRoute(
    onBack: () -> Unit,
    onAdd: (vehicleId: Long) -> Unit,
    onOpen: (reminderId: Long) -> Unit,
    onDone: (reminderId: Long) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    RemindersScreen(state, onBack, onAdd = { onAdd(viewModel.vehicleId) }, onOpen = onOpen, onDone = onDone)
}

/** Everything that comes round again on one vehicle, most urgent first. */
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDone: (Long) -> Unit,
) {
    val colors = FuelTheme.colors
    FuelScreen {
        NavHeader(title = stringResource(R.string.reminders_title), onBack = onBack) {
            state.vehicle?.let {
                Text(
                    it.name,
                    style = FuelTheme.type.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        val vehicle = state.vehicle ?: return@FuelScreen

        LazyColumn(Modifier.weight(1f)) {
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.reminders_empty),
                        style = FuelTheme.type.bodyRegular,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(Dimens.gutter),
                    )
                }
            } else {
                item(key = "top") { Spacer(Modifier.height(8.dp)) }
                items(state.rows, key = { it.first.id }) { (reminder, status) ->
                    ReminderRow(
                        reminder = reminder,
                        status = status,
                        unit = vehicle.distanceUnit,
                        onOpen = { onOpen(reminder.id) },
                        onDone = { onDone(reminder.id) },
                    )
                }
                item(key = "end") { Hairline() }
            }
        }

        PrimaryButton(
            stringResource(R.string.reminders_add),
            onAdd,
            Modifier
                .fillMaxWidth()
                .padding(start = Dimens.gutter, end = Dimens.gutter, top = 16.dp, bottom = 22.dp),
        )
    }
}
