package com.fuelexpenselog.app.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.di.FuelViewModels
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.FuelScreen
import com.fuelexpenselog.app.ui.common.NavHeader
import com.fuelexpenselog.app.ui.common.Segmented
import com.fuelexpenselog.app.ui.common.label
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.HistoryEntry

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenEntry: (HistoryEntry) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = FuelViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.missing) { if (state.missing) onBack() }
    HistoryScreen(state, onBack, viewModel::onTag, onOpenEntry)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onTag: (EntryTag?) -> Unit,
    onOpenEntry: (HistoryEntry) -> Unit,
) {
    val f = LocalFormatters.current
    val snapshot = state.snapshot
    FuelScreen {
        NavHeader(
            title = state.month?.let { f.date.monthYear(it) } ?: stringResource(R.string.history_title),
            onBack = onBack,
        )
        if (snapshot == null) return@FuelScreen
        Segmented(
            options = listOf(null, EntryTag.PERSONAL, EntryTag.BUSINESS),
            selected = state.tag,
            onSelect = onTag,
            label = { it?.label() ?: stringResource(R.string.history_all) },
            modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = FuelTheme.type.meta,
                        color = FuelTheme.colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.gutter),
                    )
                }
            }
            items(state.rows, key = { row ->
                when (val e = row.entry) {
                    is HistoryEntry.Fuel -> "f${e.fillUp.id}"
                    is HistoryEntry.Cost -> "e${e.expense.id}"
                }
            }) { row ->
                HistoryRowItem(row, snapshot.vehicle, snapshot.format, onClick = { onOpenEntry(row.entry) })
            }
        }
    }
}
