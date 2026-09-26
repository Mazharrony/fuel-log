package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.CsvRow
import com.fuelexpenselog.csv.ImportRowHash
import com.fuelexpenselog.domain.model.Decoded
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.model.decodeStrict
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.math.BigDecimal
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** What the user confirmed in the preview. Changing one re-runs [StagedImport.stage] and nothing else. */
data class ImportAssumptions(
    /** Null while the file's dates read two ways and nobody has said which. */
    val dateOrder: DateOrder?,
    val distanceUnit: DistanceUnit,
    val volumeUnit: EnergyUnit,
    /** For the app's own export holding several vehicles: whose rows to take. */
    val sourceVehicle: String? = null,
)

/** Why a row cannot be imported as it stands, or what to know about it. */
enum class RowIssue(val blocking: Boolean) {
    DATE_UNREADABLE(true),
    VOLUME_UNREADABLE(true),
    AMOUNT_UNREADABLE(true),
    ODOMETER_UNREADABLE(true),

    /** A unit, currency or type this version does not know. */
    VALUE_UNKNOWN(true),
    ODOMETER_MISSING(false),
    VOLUME_ZERO(false),
    FUTURE_DATE(false),
    CATEGORY_GUESSED(false),
}

/** A row read into what the app would store, and its fingerprint. */
sealed interface StagedEntry {
    val hash: String

    data class Fuel(val fillUp: FillUp, override val hash: String) : StagedEntry

    data class Cost(val expense: Expense, override val hash: String) : StagedEntry
}

/** One row of the preview. [entry] is null when the row could not be read. */
data class StagedRow(
    val line: Int,
    val entry: StagedEntry?,
    val issues: List<RowIssue>,
    val duplicate: Boolean = false,
) {
    val isError: Boolean get() = entry == null
    val hasWarnings: Boolean get() = entry != null && issues.any { !it.blocking }

    /** In unless it cannot be read, or it is already in the app. Re-importing a file is common. */
    val includedByDefault: Boolean get() = entry != null && !duplicate
}

/**
 * What the target vehicle already holds, for spotting rows that are already there: the same
 * fingerprint, or - for history typed in by hand - the same half-kilometre, half-day window
 * the entry screen warns about.
 */
class ExistingEntries(
    fillUps: List<FillUp>,
    expenses: List<Expense>,
    /** Hashes stored on imported rows, which still hold after the row is edited. */
    storedHashes: Collection<String> = emptyList(),
) {
    private val hashes: Set<String> = buildSet {
        addAll(storedHashes)
        fillUps.forEach { add(ImportRowHash.fillUp(it.vehicleId, it.date, it.odometerM, it.energy.micro, it.total?.micros)) }
        expenses.forEach { add(ImportRowHash.expense(it.vehicleId, it.date, it.odometerM, it.category, it.amount.micros)) }
    }
    private val readings = fillUps.filter { it.odometerM != null }

    operator fun contains(hash: String): Boolean = hash in hashes

    fun similar(fillUp: FillUp): Boolean {
        val odometer = fillUp.odometerM ?: return false
        return readings.any {
            it.vehicleId == fillUp.vehicleId &&
                abs(it.instantMillis - fillUp.instantMillis) < HALF_DAY_MS &&
                abs(it.odometerM!! - odometer) < HALF_KM
        }
    }

    companion object {
        private const val HALF_DAY_MS = 43_200_000L
        private const val HALF_KM = 500L
        val NONE = ExistingEntries(emptyList(), emptyList())
    }
}

/**
 * Rows to candidate fill-ups and expenses. Pure and quick, so an assumption changed in the
 * preview re-stages the whole file at once.
 *
 * Currency is never in these files, except the app's own: it comes from the target vehicle.
 * A row with a total keeps the total; a row with only a unit price keeps the unit price, and
 * the total is derived on display, as for one typed in. `missedPrevious` means a fill-up
 * happened before this one that is not in the file - the same direction as the app's own.
 */
object StagedImport {

    fun stage(
        plan: ImportPlan,
        assumptions: ImportAssumptions,
        target: Vehicle,
        zone: ZoneId,
        today: CivilDate,
        existing: ExistingEntries = ExistingEntries.NONE,
    ): List<StagedRow> {
        val own = plan.dialect == DialectId.FUEL_LOG
        val staged = plan.tables.flatMap { table ->
            table.table.rows.mapNotNull { row ->
                val r = RowReader(row, table.mapping)
                if (own && plan.vehicles.size > 1 && r.cell(Field.VEHICLE) != assumptions.sourceVehicle) return@mapNotNull null
                val kind = when (table.kind) {
                    RecordKind.MIXED -> if (r.cell(Field.RECORD_TYPE) == "EXPENSE") RecordKind.EXPENSES else RecordKind.FILL_UPS
                    else -> table.kind
                }
                val context = Context(plan, assumptions, target, zone, today, own)
                if (kind == RecordKind.EXPENSES) cost(r, context) else fuel(r, context)
            }
        }.sortedBy { it.line }

        // Already in the app, or earlier in this same file: excluded by default, not dropped.
        val seen = mutableSetOf<String>()
        return staged.map { row ->
            val entry = row.entry ?: return@map row
            val repeated = !seen.add(entry.hash)
            val there = entry.hash in existing || (entry is StagedEntry.Fuel && existing.similar(entry.fillUp))
            row.copy(duplicate = repeated || there)
        }
    }

    private class Context(
        val plan: ImportPlan,
        val assumptions: ImportAssumptions,
        val target: Vehicle,
        val zone: ZoneId,
        val today: CivilDate,
        val own: Boolean,
    )

    private class RowReader(val row: CsvRow, val mapping: Map<Field, Int>) {
        fun cell(field: Field): String = row.cell(mapping[field])

        /** Text exactly as written, apart from the apostrophe the app's export puts before a formula. */
        fun text(field: Field, own: Boolean): String? {
            val raw = mapping[field]?.let { row.cells.getOrNull(it) } ?: return null
            val value = if (own) unescape(raw) else raw.trim()
            return value.ifEmpty { null }
        }

        fun has(field: Field): Boolean = field in mapping
    }

    private fun fuel(r: RowReader, c: Context): StagedRow {
        val issues = mutableListOf<RowIssue>()
        val line = r.row.line
        val date = date(r, c) ?: return StagedRow(line, null, listOf(RowIssue.DATE_UNREADABLE))
        if (date > c.today) issues += RowIssue.FUTURE_DATE

        val currency = currency(r, c) ?: return StagedRow(line, null, listOf(RowIssue.VALUE_UNKNOWN))
        val unitEntered = if (c.own) {
            when (val decoded = decodeStrict<EnergyUnit>(r.cell(Field.VOLUME_UNIT))) {
                is Decoded.Known -> decoded.value
                is Decoded.Unknown -> return StagedRow(line, null, listOf(RowIssue.VALUE_UNKNOWN))
            }
        } else {
            c.assumptions.volumeUnit
        }

        val odometerM = odometer(r, c) ?: return StagedRow(line, null, listOf(RowIssue.ODOMETER_UNREADABLE))
        if (odometerM.value == null) issues += RowIssue.ODOMETER_MISSING

        val energy = r.cell(Field.VOLUME_MICRO).takeIf { c.own }?.toLongOrNull()?.let { Energy(unitEntered.kind, it) }
            ?: DecimalParser.parseOrNull(r.cell(Field.VOLUME), NumberKind.VOLUME)?.let { Energy.of(unitEntered, it) }
            ?: return StagedRow(line, null, listOf(RowIssue.VOLUME_UNREADABLE))
        if (energy.micro == 0L) issues += RowIssue.VOLUME_ZERO

        val typedUnitPrice = c.own && r.cell(Field.TYPED_FIELD) == "unit_price"
        val total = if (typedUnitPrice) null else total(r, c, currency)
        val unitPrice = if (total != null) null else money(r.cell(Field.UNIT_PRICE), NumberKind.UNIT_PRICE, currency, c.own)
        if (total == null && unitPrice == null) return StagedRow(line, null, listOf(RowIssue.AMOUNT_UNREADABLE))

        val fillUp = FillUp(
            id = 0,
            vehicleId = c.target.id,
            date = date,
            instantMillis = instant(r, date, c),
            odometerM = odometerM.value,
            energy = energy,
            energyUnitEntered = unitEntered,
            isFull = full(r, c.own),
            missedPrevious = flag(r.cell(Field.MISSED)) ?: false,
            total = total,
            unitPrice = unitPrice,
            tag = tag(r, c),
            station = r.text(Field.STATION, c.own),
            note = r.text(Field.NOTE, c.own),
        )
        val hash = ImportRowHash.fillUp(c.target.id, date, fillUp.odometerM, energy.micro, total?.micros)
        return StagedRow(line, StagedEntry.Fuel(fillUp, hash), issues)
    }

    private fun cost(r: RowReader, c: Context): StagedRow {
        val issues = mutableListOf<RowIssue>()
        val line = r.row.line
        val date = date(r, c) ?: return StagedRow(line, null, listOf(RowIssue.DATE_UNREADABLE))
        if (date > c.today) issues += RowIssue.FUTURE_DATE
        val currency = currency(r, c) ?: return StagedRow(line, null, listOf(RowIssue.VALUE_UNKNOWN))
        // An expense needs no reading: a service has one, a parking ticket does not. Some apps
        // write 0 for "none" on a cost, and no expense happens at a reading of zero.
        val reading = odometer(r, c) ?: return StagedRow(line, null, listOf(RowIssue.ODOMETER_UNREADABLE))
        val odometerM = reading.value?.takeIf { it > 0 }
        val amount = total(r, c, currency) ?: return StagedRow(line, null, listOf(RowIssue.AMOUNT_UNREADABLE))

        val title = r.text(Field.TITLE, c.own)
        val category = if (c.own) {
            runCatching { enumValueOf<ExpenseCategory>(r.cell(Field.CATEGORY)) }.getOrNull()
        } else {
            val named = c.plan.categoryNames[r.cell(Field.CATEGORY)] ?: r.cell(Field.CATEGORY)
            CategoryGuess.of(named) ?: CategoryGuess.of(title.orEmpty())
        }
        if (category == null) issues += RowIssue.CATEGORY_GUESSED

        val note = listOfNotNull(title, r.text(Field.NOTE, c.own)).joinToString(" · ").ifEmpty { null }
        val expense = Expense(
            id = 0,
            vehicleId = c.target.id,
            date = date,
            instantMillis = instant(r, date, c),
            odometerM = odometerM,
            category = category ?: ExpenseCategory.OTHER,
            amount = amount,
            tag = tag(r, c),
            vendor = r.text(Field.VENDOR, c.own),
            note = note,
        )
        val hash = ImportRowHash.expense(c.target.id, date, expense.odometerM, expense.category, amount.micros)
        return StagedRow(line, StagedEntry.Cost(expense, hash), issues)
    }

    private fun date(r: RowReader, c: Context): CivilDate? {
        val order = c.assumptions.dateOrder ?: return null
        return DateFormatResolver.parse(r.cell(Field.DATE), order)
    }

    /** Noon on the day unless the file gives a time, so the date cannot shift under any zone. */
    private fun instant(r: RowReader, date: CivilDate, c: Context): Long {
        val time = DateFormatResolver.parseTime(r.cell(Field.TIME)) ?: DateFormatResolver.time(r.cell(Field.DATE)) ?: LocalTime.NOON
        return date.toLocalDate().atTime(time).atZone(c.zone).toInstant().toEpochMilli()
    }

    /** A present-or-absent reading: null only when the cell holds something that is not a number. */
    private fun odometer(r: RowReader, c: Context): Reading? {
        if (c.own) r.cell(Field.ODOMETER_M).toLongOrNull()?.let { return Reading(it) }
        val text = r.cell(Field.ODOMETER)
        if (text.isEmpty()) return Reading(null)
        val value = DecimalParser.parseOrNull(text, NumberKind.ODOMETER) ?: return null
        return Reading(c.assumptions.distanceUnit.toMetres(value))
    }

    @JvmInline
    private value class Reading(val value: Long?)

    private fun currency(r: RowReader, c: Context): String? {
        if (!c.own) return c.target.currencyCode
        val code = r.cell(Field.CURRENCY).ifEmpty { c.target.currencyCode }
        return code.takeIf { it.length == 3 && it.all(Char::isLetter) }
    }

    private fun total(r: RowReader, c: Context, currency: String): Money? {
        if (c.own) r.cell(Field.TOTAL_MICROS).toLongOrNull()?.let { return Money(it, currency) }
        return money(r.cell(Field.TOTAL), NumberKind.MONEY_TOTAL, currency, c.own)
    }

    /** The app's own figures are exact decimals with a dot; anyone else's go through the shared parser. */
    private fun money(text: String, kind: NumberKind, currency: String, own: Boolean): Money? {
        if (text.isEmpty()) return null
        if (own) {
            return runCatching { Money(BigDecimal(text).movePointRight(6).longValueExact(), currency) }.getOrNull()
        }
        return DecimalParser.parseOrNull(text, kind)?.let { Money.of(it, currency) }
    }

    private fun full(r: RowReader, own: Boolean): Boolean = when {
        own -> r.cell(Field.FULL).toBooleanStrictOrNull() ?: true
        r.has(Field.FULL) -> flag(r.cell(Field.FULL)) ?: true
        // aCar asks the other way round: "Partial Fill-up: Yes" is a tank NOT filled.
        r.has(Field.PARTIAL) -> flag(r.cell(Field.PARTIAL))?.not() ?: true
        else -> true
    }

    private fun tag(r: RowReader, c: Context): EntryTag =
        if (c.own) decodeOr(r.cell(Field.TAG), c.target.defaultTag) else c.target.defaultTag

    private val TRUE = setOf("1", "true", "yes", "y", "x", "ja", "j", "sim", "s", "si", "oui", "o", "full", "voll", "pieno")
    private val FALSE = setOf("0", "false", "no", "n", "nein", "nao", "non", "partial", "teil", "-")

    fun flag(text: String): Boolean? {
        val value = Headers.normalize(text)
        return when (value) {
            in TRUE -> true
            in FALSE -> false
            else -> null
        }
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    /** Undoes the apostrophe the app's export puts before text a spreadsheet would run as a formula. */
    fun unescape(text: String): String =
        if (text.length >= 2 && text[0] == '\'' && text[1] in FORMULA_STARTS) text.substring(1) else text
}

/**
 * An expense category from a word in another app's export, in the languages the dialects
 * know. Words are matched by their start, so "Ölwechsel" is an oil change and "toilet" is not.
 */
object CategoryGuess {

    private val WORDS: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.OIL_CHANGE to listOf("oil", "ol", "olwechsel", "oleo", "aceite", "huile", "olio"),
        ExpenseCategory.TYRES to listOf("tyre", "tire", "reifen", "pneu", "neumatico", "llanta", "gomme"),
        ExpenseCategory.SERVICE to listOf("service", "servic", "inspection", "maintenance", "wartung", "inspektion", "revisao", "manutencao", "mantenimiento", "entretien", "tagliando"),
        ExpenseCategory.REPAIR to listOf("repair", "reparatur", "reparo", "reparacion", "reparation", "riparazione"),
        ExpenseCategory.PARTS to listOf("part", "parts", "teile", "peca", "pieza", "piece", "ricambi"),
        ExpenseCategory.TOLL to listOf("toll", "maut", "pedagio", "peaje", "peage", "pedaggio", "vignette"),
        ExpenseCategory.PARKING to listOf("parking", "parken", "parkgebuhr", "estacionamento", "aparcamiento", "stationnement", "parcheggio"),
        ExpenseCategory.INSURANCE to listOf("insurance", "versicherung", "seguro", "assurance", "assicurazione"),
        ExpenseCategory.TAX to listOf("tax", "steuer", "kfz-steuer", "imposto", "ipva", "impuesto", "taxe", "registration", "bollo"),
        ExpenseCategory.FINE to listOf("fine", "ticket", "strafe", "bussgeld", "multa", "amende", "contravvenzione"),
        ExpenseCategory.WASH to listOf("wash", "carwash", "wasche", "waschen", "lavagem", "lavado", "lavage", "lavaggio"),
    )

    fun of(text: String): ExpenseCategory? {
        if (text.isBlank()) return null
        val words = Headers.normalize(text).split(Regex("""[^\p{L}\p{N}-]+""")).filter { it.isNotEmpty() }
        return WORDS.firstOrNull { (_, keys) -> words.any { word -> keys.any { word == it || (it.length >= 3 && word.startsWith(it)) } } }?.first
    }
}
