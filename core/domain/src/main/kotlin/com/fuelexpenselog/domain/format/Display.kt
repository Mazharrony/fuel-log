package com.fuelexpenselog.domain.format

import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import kotlin.math.roundToLong

/**
 * Display precision, decided once so every screen agrees.
 *
 * km/L and the per-kWh distance formats carry two decimals because their values sit near
 * ten, where one decimal hides a real 1% change; L/100km and MPG sit where one decimal is
 * already finer than the tank-to-tank noise.
 */
fun ConsumptionFormat.decimals(): Int = when (this) {
    ConsumptionFormat.L_PER_100KM,
    ConsumptionFormat.MPG_US,
    ConsumptionFormat.MPG_UK,
    ConsumptionFormat.KWH_PER_100KM,
    -> 1

    ConsumptionFormat.KM_PER_L,
    ConsumptionFormat.KM_PER_KWH,
    ConsumptionFormat.MI_PER_KWH,
    -> 2
}

/** An odometer as the dashboard shows it: whole units, rounded half-up. */
fun DistanceUnit.wholeUnits(metres: Long): Long = fromMetres(metres).roundToLong()

/**
 * Converts a cost per kilometre into a cost per [unit] of distance - 0.08 EUR/km becomes
 * 0.129 EUR/mi. Call it on the value `costPerKm()` returns, never on a total.
 */
fun Money.perDistanceUnit(unit: DistanceUnit): Money = this * (unit.metresPerUnit / 1000.0)
