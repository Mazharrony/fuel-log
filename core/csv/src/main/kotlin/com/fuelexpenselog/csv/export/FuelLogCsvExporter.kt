package com.fuelexpenselog.csv.export

import com.fuelexpenselog.csv.CsvWriter
import com.fuelexpenselog.domain.format.Rounding
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One file with every fill-up and expense, for a spreadsheet, an accountant, or another app.
 *
 * Machine-readable before anything else: dates are ISO, enums are their names, numbers use
 * a dot and no grouping under every locale - an Arabic or German phone writes the same bytes
 * as an American one. Values appear in the unit they were entered in, for a person reading
 * the file, and again in canonical units, so the app's own import reads them back exactly.
 */
object FuelLogCsvExporter {

    val HEADER: List<String> = listOf(
        "record_type", "vehicle", "date",
        "odometer", "distance_unit",
        "volume", "volume_unit", "full_tank", "missed_previous",
        "total", "unit_price", "currency", "typed_field",
        "category", "tag", "station", "vendor", "note",
        "odometer_m", "volume_micro", "total_micros",
    )

    const val FILL_UP = "FILL_UP"
    const val EXPENSE = "EXPENSE"

    /**
     * Rows ordered by vehicle, then by date and time, fill-ups and expenses interleaved as
     * they happened.
     */
    fun write(
        vehicles: List<Vehicle>,
        fillUps: List<FillUp>,
        expenses: List<Expense>,
        out: Appendable,
        bom: Boolean = true,
    ) {
        val csv = CsvWriter(out, writeBom = bom)
        csv.writeRow(HEADER)
        val byId = vehicles.associateBy { it.id }
        val rows = fillUps.map { Row(it.vehicleId, it.date.value, it.instantMillis, fillUp = it) } +
            expenses.map { Row(it.vehicleId, it.date.value, it.instantMillis, expense = it) }
        rows.filter { it.vehicleId in byId }
            .sortedWith(compareBy<Row> { byId.getValue(it.vehicleId).name }.thenBy { it.vehicleId }.thenBy { it.date }.thenBy { it.instant })
            .forEach { row ->
                val vehicle = byId.getValue(row.vehicleId)
                csv.writeRow(row.fillUp?.let { fillUpRow(vehicle, it) } ?: expenseRow(vehicle, row.expense!!))
            }
    }

    private class Row(val vehicleId: Long, val date: Int, val instant: Long, val fillUp: FillUp? = null, val expense: Expense? = null)

    private fun fillUpRow(vehicle: Vehicle, f: FillUp): List<String?> {
        val entered = f.energyUnitEntered
        val volume = f.energy.inUnit(entered)
        val total = f.totalOrDerived()
        // The unit price per the unit it was entered in: stored exactly, or derived from the
        // total and rounded to the three decimals a pump prices in.
        val unitPrice = f.unitPrice
            ?: total?.takeIf { volume > 0.0 }?.let { Money.of(Rounding.to(it.asDouble / volume, 3), it.currency) }
        return listOf(
            FILL_UP,
            text(vehicle.name),
            f.date.toString(),
            f.odometerM?.let { decimal(vehicle.distanceUnit.fromMetres(it), 1) },
            vehicle.distanceUnit.name,
            decimal(volume, 3),
            entered.name,
            f.isFull.toString(),
            f.missedPrevious.toString(),
            total?.let { money(it.micros) },
            unitPrice?.let { money(it.micros) },
            (f.total ?: f.unitPrice)?.currency,
            if (f.total != null) "total" else "unit_price",
            null,
            f.tag.name,
            text(f.station),
            null,
            text(f.note),
            f.odometerM?.toString(),
            f.energy.micro.toString(),
            total?.micros?.toString(),
        )
    }

    private fun expenseRow(vehicle: Vehicle, e: Expense): List<String?> = listOf(
        EXPENSE,
        text(vehicle.name),
        e.date.toString(),
        e.odometerM?.let { decimal(vehicle.distanceUnit.fromMetres(it), 1) },
        vehicle.distanceUnit.name,
        null, null, null, null,
        money(e.amount.micros),
        null,
        e.amount.currency,
        "total",
        e.category.name,
        e.tag.name,
        null,
        text(e.vendor),
        text(e.note),
        e.odometerM?.toString(),
        null,
        e.amount.micros.toString(),
    )

    /** A number with at most [maxDecimals], no trailing zeros, no grouping, always a dot. */
    fun decimal(value: Double, maxDecimals: Int): String =
        BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Exact to the micro, never fewer than two decimals: 58.396 stays 58.396, 60 is 60.00. */
    fun money(micros: Long): String {
        val exact = BigDecimal.valueOf(micros, 6).stripTrailingZeros()
        return (if (exact.scale() < 2) exact.setScale(2) else exact).toPlainString()
    }

    /**
     * Free text a person typed. A cell starting with = + - @ is a formula to a spreadsheet,
     * so a station called "=HYPERLINK(...)" would run when the accountant opens the file. A
     * leading apostrophe makes it text; the app's own import takes it off again.
     */
    fun text(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        return if (value.first() in FORMULA_STARTS) "'$value" else value
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
