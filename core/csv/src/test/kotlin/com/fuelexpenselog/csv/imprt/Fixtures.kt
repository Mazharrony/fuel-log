package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.export.FuelLogCsvExporter
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
import java.time.ZoneOffset

/** The synthetic fixtures, and a small history for the app's own export. */
object Fixtures {

    val ZONE: ZoneOffset = ZoneOffset.UTC
    val TODAY: CivilDate = CivilDate.of(2026, 9, 26)

    fun bytes(name: String): ByteArray =
        checkNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "no fixture $name" }.readBytes()

    fun plan(name: String): ImportPlan = ImportPlanner.plan(bytes(name))

    fun vehicle(
        id: Long = 7,
        name: String = "Golf",
        distance: DistanceUnit = DistanceUnit.KILOMETRE,
        volume: EnergyUnit = EnergyUnit.LITRE,
        currency: String = "EUR",
    ) = Vehicle(
        id = id,
        name = name,
        type = VehicleType.CAR,
        fuelType = FuelType.PETROL,
        distanceUnit = distance,
        volumeUnit = volume,
        energyUnit = EnergyUnit.KWH,
        consumptionFormat = null,
        currencyCode = currency,
        tankCapacity = null,
        batteryCapacity = null,
        defaultTag = EntryTag.PERSONAL,
    )

    /** Assumptions as the preview would first propose them for [plan] into [target]. */
    fun assumptions(plan: ImportPlan, target: Vehicle) = ImportAssumptions(
        dateOrder = (plan.dates as? DateFormatResolver.Result.Resolved)?.order,
        distanceUnit = plan.distanceUnit ?: target.distanceUnit,
        volumeUnit = plan.volumeUnit ?: target.volumeUnit,
        sourceVehicle = plan.vehicles.firstOrNull(),
    )

    fun stage(plan: ImportPlan, target: Vehicle, existing: ExistingEntries = ExistingEntries.NONE): List<StagedRow> =
        StagedImport.stage(plan, assumptions(plan, target), target, ZONE, TODAY, existing)

    /** A history with every kind of value the export writes, awkward text included. */
    fun history(vehicleId: Long): Pair<List<FillUp>, List<Expense>> {
        fun day(n: Long) = CivilDate.of(2026, 1, 1).plusDays(n)
        val fillUps = listOf(
            FillUp(1, vehicleId, day(3), 1, 48_200_000, Energy.of(EnergyUnit.LITRE, 40.0), EnergyUnit.LITRE, true, false, Money.of(60.0, "EUR"), null, EntryTag.PERSONAL, station = "Aral, Mitte"),
            FillUp(2, vehicleId, day(17), 2, 48_700_450, Energy.of(EnergyUnit.LITRE, 41.537), EnergyUnit.LITRE, true, false, null, Money.of(1.459, "EUR"), EntryTag.BUSINESS, note = "=HYPERLINK(\"x\")"),
            FillUp(3, vehicleId, day(31), 3, null, Energy.of(EnergyUnit.US_GALLON, 5.25), EnergyUnit.US_GALLON, false, true, Money.of(31.07, "EUR"), null, EntryTag.PERSONAL, note = "said \"half\"\nthen home"),
            FillUp(4, vehicleId, day(45), 4, 49_600_000, Energy.of(EnergyUnit.LITRE, 38.0), EnergyUnit.LITRE, true, false, Money.of(58.9, "EUR"), null, EntryTag.PERSONAL, note = "  spaced  "),
        )
        val expenses = listOf(
            Expense(5, vehicleId, day(19), 5, 48_750_000, ExpenseCategory.OIL_CHANGE, Money.of(89.9, "EUR"), EntryTag.PERSONAL, vendor = "-Quick Lube", note = "5W-30"),
            Expense(6, vehicleId, day(32), 6, null, ExpenseCategory.INSURANCE, Money.of(420.0, "EUR"), EntryTag.BUSINESS),
        )
        return fillUps to expenses
    }

    fun ownExport(vehicles: List<Vehicle>, fillUps: List<FillUp>, expenses: List<Expense>): ByteArray {
        val out = StringBuilder()
        FuelLogCsvExporter.write(vehicles, fillUps, expenses, out)
        return out.toString().toByteArray(Charsets.UTF_8)
    }
}
