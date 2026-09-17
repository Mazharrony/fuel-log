package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.FillUp
import java.time.LocalDate
import java.time.ZoneOffset

/** 2026-01-01T00:00:00Z, so day offsets read as plain integers in tests. */
const val DAY: Long = 24L * 60 * 60 * 1000
val EPOCH_BASE: Long = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun day(n: Int): Long = EPOCH_BASE + n * DAY

fun fill(
    odometer: Double,
    volume: Double,
    full: Boolean = true,
    cost: Double = volume * 2.0,
    day: Int = 0,
    missed: Boolean = false,
) = FillUp(
    vehicleId = 1,
    date = day(day),
    odometer = odometer,
    volume = volume,
    totalCost = cost,
    isFullTank = full,
    isMissedEntry = missed,
)

fun expense(
    category: ExpenseCategory,
    cost: Double = 100.0,
    odometer: Double? = null,
    day: Int = 0,
) = Expense(
    vehicleId = 1,
    date = day(day),
    odometer = odometer,
    category = category,
    totalCost = cost,
)
