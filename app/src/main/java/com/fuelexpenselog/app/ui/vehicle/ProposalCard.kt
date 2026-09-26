package com.fuelexpenselog.app.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuelexpenselog.app.R
import com.fuelexpenselog.app.format.LocalFormatters
import com.fuelexpenselog.app.ui.common.OutlineButton
import com.fuelexpenselog.app.ui.theme.Dimens
import com.fuelexpenselog.app.ui.theme.FuelTheme
import com.fuelexpenselog.domain.consumption.OdometerAnomaly
import com.fuelexpenselog.domain.consumption.OdometerProposal
import com.fuelexpenselog.domain.consumption.SegmentReason
import com.fuelexpenselog.domain.unit.DistanceUnit

class ProposalCallbacks(
    val onCorrect: (OdometerProposal) -> Unit,
    val onOpen: (OdometerProposal) -> Unit,
    val onBridge: (OdometerProposal) -> Unit,
    val onReset: (OdometerProposal, SegmentReason) -> Unit,
    val onDismiss: (OdometerProposal) -> Unit,
)

/**
 * The engine noticed something about a reading and is asking. Every action is explicit;
 * "Leave it" records the answer so the same question does not come back.
 */
@Composable
fun ProposalCard(
    proposal: OdometerProposal,
    unit: DistanceUnit,
    sameDayConflict: Boolean,
    callbacks: ProposalCallbacks,
    modifier: Modifier = Modifier,
) {
    val f = LocalFormatters.current
    val colors = FuelTheme.colors
    val recorded = f.distance.format(proposal.recordedM, unit)
    val date = f.date.medium(proposal.date)

    val (title, body) = when (proposal.anomaly) {
        OdometerAnomaly.SUSPECTED_TYPO -> stringResource(R.string.proposal_typo_title) to (
            proposal.correctedM?.let { stringResource(R.string.proposal_typo_body, recorded, date, f.distance.format(it, unit)) }
                ?: stringResource(R.string.proposal_typo_unknown, recorded, date)
            )
        OdometerAnomaly.ROLLOVER -> stringResource(R.string.proposal_rollover_title) to (
            if (sameDayConflict) stringResource(R.string.proposal_rollover_same_day, date)
            else stringResource(R.string.proposal_rollover_body, recorded, date)
            )
        OdometerAnomaly.RESET -> stringResource(R.string.proposal_reset_title) to stringResource(R.string.proposal_reset_body, recorded, date)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(Dimens.hairline, colors.textPrimary)
            .background(colors.surface)
            .padding(15.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = FuelTheme.type.body, color = colors.textPrimary)
        Text(body, style = FuelTheme.type.meta, color = colors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (proposal.anomaly) {
                OdometerAnomaly.SUSPECTED_TYPO -> {
                    val corrected = proposal.correctedM
                    // Outlined, not yellow: the screen's one yellow action is Add fill-up.
                    if (corrected != null) {
                        OutlineButton(
                            stringResource(R.string.proposal_typo_fix, f.distance.format(corrected, unit)),
                            { callbacks.onCorrect(proposal) },
                        )
                    } else {
                        OutlineButton(stringResource(R.string.proposal_open), { callbacks.onOpen(proposal) })
                    }
                }
                OdometerAnomaly.ROLLOVER -> {
                    if (sameDayConflict) {
                        OutlineButton(stringResource(R.string.proposal_open), { callbacks.onOpen(proposal) })
                    } else {
                        OutlineButton(stringResource(R.string.proposal_rollover_fix), { callbacks.onBridge(proposal) })
                    }
                }
                OdometerAnomaly.RESET -> {
                    OutlineButton(stringResource(R.string.proposal_reset_replaced), { callbacks.onReset(proposal, SegmentReason.UNIT_REPLACED) })
                    OutlineButton(stringResource(R.string.proposal_reset_bought), { callbacks.onReset(proposal, SegmentReason.PURCHASED_USED) })
                }
            }
            OutlineButton(stringResource(R.string.proposal_dismiss), { callbacks.onDismiss(proposal) })
        }
    }
}
