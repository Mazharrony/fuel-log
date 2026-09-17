package com.fuelexpenselog.app.data

import com.fuelexpenselog.app.data.db.ExpenseEntity
import com.fuelexpenselog.app.data.db.FillUpEntity
import com.fuelexpenselog.app.data.db.VehicleEntity
import com.fuelexpenselog.app.domain.model.Expense
import com.fuelexpenselog.app.domain.model.FillUp
import com.fuelexpenselog.app.domain.model.Vehicle

/**
 * The only reason an app this small keeps a mapper layer: it lets the engine's
 * signature stay exactly as the spec wrote it, taking a plain domain FillUp,
 * while the storage schema remains free to change underneath.
 */
fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    name = name,
    distanceUnit = distanceUnit,
    volumeUnit = volumeUnit,
    currency = currency,
    tankCapacityLitres = tankCapacityLitres,
    isActive = isActive,
)

fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    distanceUnit = distanceUnit,
    volumeUnit = volumeUnit,
    currency = currency,
    tankCapacityLitres = tankCapacityLitres,
    isActive = isActive,
)

fun FillUpEntity.toDomain() = FillUp(
    id = id,
    vehicleId = vehicleId,
    date = date,
    odometer = odometer,
    volume = volume,
    totalCost = totalCost,
    isFullTank = isFullTank,
    isMissedEntry = isMissedEntry,
    fuelType = fuelType,
    note = note,
)

fun FillUp.toEntity() = FillUpEntity(
    id = id,
    vehicleId = vehicleId,
    date = date,
    odometer = odometer,
    volume = volume,
    totalCost = totalCost,
    isFullTank = isFullTank,
    isMissedEntry = isMissedEntry,
    fuelType = fuelType,
    note = note,
)

fun ExpenseEntity.toDomain() = Expense(
    id = id,
    vehicleId = vehicleId,
    date = date,
    odometer = odometer,
    category = category,
    totalCost = totalCost,
    note = note,
)

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    date = date,
    odometer = odometer,
    category = category,
    totalCost = totalCost,
    note = note,
)
