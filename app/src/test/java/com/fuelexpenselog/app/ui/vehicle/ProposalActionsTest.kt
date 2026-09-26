package com.fuelexpenselog.app.ui.vehicle

import com.fuelexpenselog.app.data.DbFixtures
import com.fuelexpenselog.app.testing.TestDb
import com.fuelexpenselog.domain.consumption.OdometerAnomaly
import com.fuelexpenselog.domain.consumption.SegmentReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProposalActionsTest {

    private val t = TestDb()
    private val actions = ProposalActions(t.repo, t.prefs)

    @After
    fun tearDown() = t.close()

    private suspend fun consumption(v: Long) = t.repo.observeConsumption(t.repo.vehicle(v)!!).first()

    @Test
    fun `confirming a typo writes the single correction into that fill-up`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 10_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 10_500.0, litres = 40.0))
        val typo = t.repo.addFillUp(DbFixtures.fillUp(v, day = 14, odometerKm = 1_070.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 21, odometerKm = 10_900.0, litres = 40.0))

        val proposal = consumption(v).proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.SUSPECTED_TYPO)
        assertThat(proposal.correctedM).isEqualTo(10_700_000)

        assertThat(actions.correctTypo(proposal)).isEqualTo(ProposalActions.Outcome.APPLIED)

        assertThat(t.repo.fillUp(typo)!!.odometerM).isEqualTo(10_700_000)
        assertThat(consumption(v).proposals).isEmpty()
    }

    @Test
    fun `confirming a rollover bridges it, and the span across the wrap is measured`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 99_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 99_900.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 14, odometerKm = 350.0, litres = 30.0))

        val proposal = consumption(v).proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.ROLLOVER)
        assertThat(proposal.offsetM).isEqualTo(100_000_000)

        assertThat(actions.bridgeRollover(v, proposal)).isEqualTo(ProposalActions.Outcome.APPLIED)

        val after = consumption(v)
        assertThat(after.proposals).isEmpty()
        // 99,900 -> 100,350 once bridged: 450 km on the 30 L that filled it back up.
        assertThat(after.measured.last().distanceM).isEqualTo(450_000)
    }

    @Test
    fun `a wrap straddled by two same-day fill-ups is refused, not bridged wrongly`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 99_960.0, litres = 40.0))
        // Same day: one reading after the wrap, one just before it.
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 200.0, litres = 30.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 99_950.0, litres = 5.0))

        val proposal = consumption(v).proposals.single { it.anomaly == OdometerAnomaly.ROLLOVER }
        assertThat(actions.bridgeRollover(v, proposal)).isEqualTo(ProposalActions.Outcome.SAME_DAY_CONFLICT)
        assertThat(t.db.odometerSegmentDao().all()).isEmpty()
    }

    @Test
    fun `a dismissed proposal stays hidden when the data re-emits, until its reading changes`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 50_000.0, litres = 40.0))
        val reset = t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 20.0, litres = 40.0))

        val proposal = consumption(v).proposals.single()
        actions.dismiss(v, proposal)

        // An unrelated change re-runs the engine; the same question must not come back.
        t.repo.addExpense(DbFixtures.expense(v, day = 8))
        assertThat(actions.visible(v, consumption(v).proposals, t.prefs.dismissedProposals)).isEmpty()

        // A different reading is a different question.
        t.repo.updateFillUp(t.repo.fillUp(reset)!!.copy(odometerM = 30_000))
        assertThat(actions.visible(v, consumption(v).proposals, t.prefs.dismissedProposals)).hasSize(1)
    }

    @Test
    fun `declaring a reset breaks the chain and answers the question`() = runTest {
        val v = t.repo.addVehicle(DbFixtures.vehicle())
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 0, odometerKm = 50_000.0, litres = 40.0))
        t.repo.addFillUp(DbFixtures.fillUp(v, day = 7, odometerKm = 20.0, litres = 40.0))
        val proposal = consumption(v).proposals.single()
        assertThat(proposal.anomaly).isEqualTo(OdometerAnomaly.RESET)

        actions.declareReset(v, proposal, SegmentReason.UNIT_REPLACED)

        assertThat(t.db.odometerSegmentDao().all().single().reason).isEqualTo(SegmentReason.UNIT_REPLACED.name)
        assertThat(actions.visible(v, consumption(v).proposals, t.prefs.dismissedProposals)).isEmpty()
    }
}
