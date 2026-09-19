package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.money.CurrencySubtotals
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind

/**
 * The headline figures for one vehicle and one energy kind.
 */
data class ConsumptionSummary(
    val kind: EnergyKind,
    val measuredCount: Int,
    val totalDistanceM: Long,
    val totalEnergy: Energy,
    /** Null when the spans mix currencies. Render per-currency subtotals instead. */
    val totalCost: Money?,
    val best: Measured?,
    val worst: Measured?,
    val latest: Measured?,
) {

    /**
     * The lifetime figure, as **total distance over total energy**.
     *
     * Never the mean of the per-span ratios. Averaging ratios weights a 200 km span the same
     * as a 900 km one and comes out several percent off - it is the single most common bug
     * in this category of app, and it is invisible because the wrong answer is still a
     * believable number.
     */
    val lifetimeKmPerUnit: Double?
        get() = ConsumptionFormat.kmPerUnit(totalDistanceM, totalEnergy)

    fun lifetimeAs(format: ConsumptionFormat): Double? {
        require(format.kind == kind) { "$format cannot express a $kind figure" }
        return lifetimeKmPerUnit?.let(format::fromKmPerUnit)?.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Cost per kilometre across everything measured, or null when cost is not comparable. */
    fun costPerKm(): Money? {
        val cost = totalCost ?: return null
        if (totalDistanceM <= 0L) return null
        return cost * (1000.0 / totalDistanceM)
    }

    companion object {
        fun of(kind: EnergyKind, measured: List<Measured>): ConsumptionSummary? {
            if (measured.isEmpty()) return null

            val distance = measured.sumOf { it.distanceM }
            val energy = Energy(kind, measured.sumOf { it.energy.micro })

            return ConsumptionSummary(
                kind = kind,
                measuredCount = measured.size,
                totalDistanceM = distance,
                totalEnergy = energy,
                totalCost = CurrencySubtotals.singleCurrencyTotal(measured.mapNotNull { it.cost }),
                best = measured.maxByOrNull { it.kmPerUnit },
                worst = measured.minByOrNull { it.kmPerUnit },
                latest = measured.last(),
            )
        }

        /**
         * The rolling figure shown on the chart header - again summed, not averaged.
         * [spans] is taken from the most recent end of the timeline.
         */
        fun recentKmPerUnit(measured: List<Measured>, spans: Int): Double? {
            val recent = measured.takeLast(spans)
            if (recent.isEmpty()) return null

            return ConsumptionFormat.kmPerUnit(
                recent.sumOf { it.distanceM },
                Energy(recent.first().kind, recent.sumOf { it.energy.micro }),
            )
        }
    }
}
