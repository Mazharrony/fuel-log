package com.fuelexpenselog.app.data

import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit

object DbFixtures {

    val BASE: CivilDate = CivilDate.of(2026, 1, 1)
    const val NOW = 1_767_225_600_000L

    fun vehicle(
        name: String = "Honda Civic",
        distance: DistanceUnit = DistanceUnit.KILOMETRE,
        volume: EnergyUnit = EnergyUnit.LITRE,
        currency: String = "EUR",
        tankLitres: Double? = 47.0,
    ) = Vehicle(
        id = 0,
        name = name,
        type = VehicleType.CAR,
        fuelType = FuelType.PETROL,
        distanceUnit = distance,
        volumeUnit = volume,
        energyUnit = EnergyUnit.KWH,
        consumptionFormat = null,
        currencyCode = currency,
        tankCapacity = tankLitres?.let { Energy.of(EnergyUnit.LITRE, it) },
        batteryCapacity = null,
        defaultTag = EntryTag.PERSONAL,
    )

    fun fillUp(
        vehicleId: Long,
        day: Int,
        odometerKm: Double?,
        litres: Double,
        full: Boolean = true,
        missed: Boolean = false,
        total: Double? = null,
        currency: String = "EUR",
        tag: EntryTag = EntryTag.PERSONAL,
        id: Long = 0,
    ) = FillUp(
        id = id,
        vehicleId = vehicleId,
        date = BASE.plusDays(day.toLong()),
        instantMillis = NOW + day * 86_400_000L,
        odometerM = odometerKm?.let { DistanceUnit.KILOMETRE.toMetres(it) },
        energy = Energy.of(EnergyUnit.LITRE, litres),
        energyUnitEntered = EnergyUnit.LITRE,
        isFull = full,
        missedPrevious = missed,
        total = Money.of(total ?: (litres * 1.5), currency),
        unitPrice = null,
        tag = tag,
    )

    fun expense(
        vehicleId: Long,
        day: Int,
        category: ExpenseCategory = ExpenseCategory.SERVICE,
        amount: Double = 100.0,
        odometerKm: Double? = null,
        currency: String = "EUR",
        tag: EntryTag = EntryTag.PERSONAL,
        id: Long = 0,
    ) = Expense(
        id = id,
        vehicleId = vehicleId,
        date = BASE.plusDays(day.toLong()),
        instantMillis = NOW + day * 86_400_000L,
        odometerM = odometerKm?.let { DistanceUnit.KILOMETRE.toMetres(it) },
        category = category,
        amount = Money.of(amount, currency),
        tag = tag,
    )
}
