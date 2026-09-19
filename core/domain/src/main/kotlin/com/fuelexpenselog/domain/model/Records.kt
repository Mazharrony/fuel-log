package com.fuelexpenselog.domain.model

import com.fuelexpenselog.domain.consumption.FuelEvent
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit

/**
 * Records that came back from storage but contain a value this build does not understand -
 * a unit or currency written by a newer version.
 *
 * They are shown, because hiding somebody's data is worse, and they are **not editable**,
 * because saving one would write this build's guess over the real value. Restoring a newer
 * backup, editing a note, and permanently losing the real category is the exact scenario
 * this prevents.
 */
interface MaybeUnreadable {
    /** Raw values this build did not recognise, e.g. `["volumeUnit=HYDROGEN_KG"]`. */
    val unreadable: List<String>

    val isEditable: Boolean get() = unreadable.isEmpty()
}

data class Vehicle(
    val id: Long,
    val name: String,
    val type: VehicleType,
    val fuelType: FuelType,
    val distanceUnit: DistanceUnit,
    /** Always a LIQUID unit. The kWh case lives in [energyUnit]. */
    val volumeUnit: EnergyUnit,
    val energyUnit: EnergyUnit,
    /** Null means "inherit the app-level default". */
    val consumptionFormat: ConsumptionFormat?,
    val currencyCode: String,
    val tankCapacity: Energy?,
    val batteryCapacity: Energy?,
    val defaultTag: EntryTag,
    val make: String? = null,
    val model: String? = null,
    val plate: String? = null,
    val modelYear: Int? = null,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAtMillis: Long = 0,
    override val unreadable: List<String> = emptyList(),
) : MaybeUnreadable {

    init {
        require(volumeUnit.kind == EnergyKind.LIQUID) {
            "A vehicle's volume unit must be a liquid measure, got $volumeUnit"
        }
    }

    /**
     * Which format this vehicle's figures are shown in, for one energy kind.
     *
     * The vehicle wins when it has an opinion that fits the kind - a UK car and a US car in
     * one garage genuinely want MPG(UK) and MPG(US). Otherwise the app-level setting is used
     * if it fits, and failing that the natural default for the kind, so asking for an
     * electric figure while the app is set to MPG still returns something electric.
     */
    fun formatFor(kind: EnergyKind, appDefault: ConsumptionFormat): ConsumptionFormat {
        consumptionFormat?.let { if (it.kind == kind) return it }
        if (appDefault.kind == kind) return appDefault
        return ConsumptionFormat.defaultFor(kind)
    }
}

data class FillUp(
    val id: Long,
    val vehicleId: Long,
    val date: CivilDate,
    val instantMillis: Long,
    val odometerM: Long?,
    val energy: Energy,
    /** What the user typed it in, so an edit shows that back rather than a conversion. */
    val energyUnitEntered: EnergyUnit,
    val isFull: Boolean,
    /** True means a fill-up happened BEFORE this one that is not recorded. */
    val missedPrevious: Boolean,
    val total: Money?,
    val unitPrice: Money?,
    val tag: EntryTag,
    val station: String? = null,
    val fuelGrade: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    override val unreadable: List<String> = emptyList(),
) : MaybeUnreadable {

    /**
     * Exactly one of total and unit price is persisted; the other is derived here. Storing
     * both would put a rounded number the user never typed into their tax export.
     */
    fun totalOrDerived(): Money? = total ?: unitPrice?.let { price ->
        val units = energy.micro / 1_000_000.0
        Money.of(price.asDouble * units, price.currency)
    }

    fun unitPriceOrDerived(): Money? = unitPrice ?: total?.let { paid ->
        val units = energy.micro / 1_000_000.0
        if (units <= 0.0) null else Money.of(paid.asDouble / units, paid.currency)
    }

    fun toFuelEvent(): FuelEvent = FuelEvent(
        id = id,
        date = date,
        instantMillis = instantMillis,
        odometerM = odometerM,
        energy = energy,
        isFull = isFull,
        missedPrevious = missedPrevious,
        cost = totalOrDerived(),
    )
}

data class Expense(
    val id: Long,
    val vehicleId: Long,
    val date: CivilDate,
    val instantMillis: Long,
    /** Optional: a service has a reading, a parking ticket does not. Recording one is what
     *  powers the "last done at" lines and every distance-based reminder. */
    val odometerM: Long?,
    val category: ExpenseCategory,
    val amount: Money,
    val tag: EntryTag,
    val vendor: String? = null,
    val note: String? = null,
    val reminderId: Long? = null,
    override val unreadable: List<String> = emptyList(),
) : MaybeUnreadable

/** A fill-up and an expense side by side, for the combined history list. */
sealed interface HistoryEntry {
    val date: CivilDate
    val instantMillis: Long
    val amount: Money?
    val odometerM: Long?

    @JvmInline
    value class Fuel(val fillUp: FillUp) : HistoryEntry {
        override val date get() = fillUp.date
        override val instantMillis get() = fillUp.instantMillis
        override val amount get() = fillUp.totalOrDerived()
        override val odometerM get() = fillUp.odometerM
    }

    @JvmInline
    value class Cost(val expense: Expense) : HistoryEntry {
        override val date get() = expense.date
        override val instantMillis get() = expense.instantMillis
        override val amount get() = expense.amount
        override val odometerM get() = expense.odometerM
    }
}

/**
 * The combined timeline, newest first.
 *
 * Merged in Kotlin rather than with a SQL UNION: a union cannot use an index for the merged
 * ORDER BY, and this is trivially testable.
 */
fun buildHistory(fillUps: List<FillUp>, expenses: List<Expense>): List<HistoryEntry> =
    (fillUps.map(HistoryEntry::Fuel) + expenses.map(HistoryEntry::Cost))
        .sortedWith(
            compareByDescending<HistoryEntry> { it.date.value }
                .thenByDescending { it.instantMillis },
        )
