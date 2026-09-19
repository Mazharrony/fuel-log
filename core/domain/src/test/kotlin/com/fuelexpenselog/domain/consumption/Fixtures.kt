package com.fuelexpenselog.domain.consumption

import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit

/**
 * Builders that let a scenario read like the situation it describes. Everything is in the
 * units a person would say out loud - kilometres and litres - and converted at the edge.
 */
object Fixtures {

    private val BASE = CivilDate.of(2026, 1, 1)

    private var nextId = 1L

    /** The date N days after the fixture base, for declaring a segment in a test. */
    fun day(n: Int): CivilDate = BASE.plusDays(n.toLong())

    fun resetIds() { nextId = 1L }

    fun fill(
        day: Int,
        odometerKm: Double?,
        litres: Double,
        full: Boolean = true,
        missed: Boolean = false,
        cost: Double? = null,
        currency: String = "EUR",
        id: Long = nextId++,
    ): FuelEvent = FuelEvent(
        id = id,
        date = BASE.plusDays(day.toLong()),
        instantMillis = day * 86_400_000L,
        odometerM = odometerKm?.let { DistanceUnit.KILOMETRE.toMetres(it) },
        energy = Energy.of(EnergyUnit.LITRE, litres),
        isFull = full,
        missedPrevious = missed,
        cost = cost?.let { Money.of(it, currency) },
    )

    fun charge(
        day: Int,
        odometerKm: Double?,
        kwh: Double,
        full: Boolean = true,
        cost: Double? = null,
        currency: String = "EUR",
        id: Long = nextId++,
    ): FuelEvent = FuelEvent(
        id = id,
        date = BASE.plusDays(day.toLong()),
        instantMillis = day * 86_400_000L,
        odometerM = odometerKm?.let { DistanceUnit.KILOMETRE.toMetres(it) },
        energy = Energy.of(EnergyUnit.KWH, kwh),
        isFull = full,
        cost = cost?.let { Money.of(it, currency) },
    )

    /** Miles, for the typo and rollover scenarios where digits matter. */
    fun fillMiles(
        day: Int,
        odometerMiles: Double?,
        gallons: Double,
        full: Boolean = true,
        id: Long = nextId++,
    ): FuelEvent = FuelEvent(
        id = id,
        date = BASE.plusDays(day.toLong()),
        instantMillis = day * 86_400_000L,
        odometerM = odometerMiles?.let { DistanceUnit.MILE.toMetres(it) },
        energy = Energy.of(EnergyUnit.US_GALLON, gallons),
        isFull = full,
    )

    fun run(
        events: List<FuelEvent>,
        kind: EnergyKind = EnergyKind.LIQUID,
        declared: List<DeclaredSegment> = emptyList(),
        unit: DistanceUnit = DistanceUnit.KILOMETRE,
    ): ConsumptionResult = ConsumptionEngine.compute(events, kind, declared, unit)

    fun ConsumptionResult.gapReasons(): List<GapReason> =
        timeline.filterIsInstance<Gap>().map { it.reason }

    fun ConsumptionResult.kmPerLitre(): List<Double> =
        measured.map { it.kmPerUnit }
}
