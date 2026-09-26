package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.CharsetSniffer
import com.fuelexpenselog.csv.CsvTable
import com.fuelexpenselog.csv.CsvTokenizer
import com.fuelexpenselog.csv.DelimiterSniffer
import com.fuelexpenselog.domain.parse.DecimalParser
import com.fuelexpenselog.domain.parse.NumberKind
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit

/** A table the importer will stage, and which of its columns mean what. */
data class MappedTable(val kind: RecordKind, val table: CsvTable, val mapping: Map<Field, Int>)

/** What the importer made of a file before anyone chose anything. */
data class ImportPlan(
    val encoding: CharsetSniffer.Encoding,
    val delimiter: Char,
    val dialect: DialectId,
    val tables: List<MappedTable>,
    val dates: DateFormatResolver.Result,
    /** Units the file states in its headers. Null where it says nothing: the vehicle's apply. */
    val distanceUnit: DistanceUnit?,
    val volumeUnit: EnergyUnit?,
    /** The vehicles named in the app's own export. One is imported at a time. */
    val vehicles: List<String> = emptyList(),
    /** Fuelio's cost categories, by id. */
    val categoryNames: Map<String, String> = emptyMap(),
) {
    val rowCount: Int get() = tables.sumOf { it.table.rows.size }

    /** For the column mapping of a generic file: the header it is mapped from. */
    val header: List<String> get() = tables.firstOrNull()?.table?.header.orEmpty()
}

/**
 * Bytes to a plan, in pure stages: decode, split Fuelio-style sections, find each section's
 * delimiter, tokenize, name the dialect, map its columns, and read the dates and units the
 * file states. Nothing here knows about Android; the app hands over bytes.
 */
object ImportPlanner {

    private const val SECTION_MARKER = "## "

    fun plan(bytes: ByteArray): ImportPlan {
        val decoded = CharsetSniffer.decode(bytes)
        val (document, delimiter) = read(decoded.text)
        val dialect = DialectDetector.detect(document)
        val tables = if (dialect == DialectId.GENERIC) {
            document.main?.let { listOf(MappedTable(RecordKind.FILL_UPS, it, Headers.map(it.header, Dialects.GENERIC_FILL_UPS.columns))) }.orEmpty()
        } else {
            Dialects.of(dialect).tables.mapNotNull { spec ->
                val table = (if (spec.section != null) document.section(spec.section) else document.main) ?: return@mapNotNull null
                val mapping = Headers.map(table.header, spec.columns)
                if (spec.columns.any { it.required && it.field !in mapping }) null else MappedTable(spec.kind, table, mapping)
            }
        }
        return ImportPlan(
            encoding = decoded.encoding,
            delimiter = delimiter,
            dialect = dialect,
            tables = tables,
            dates = resolveDates(tables),
            distanceUnit = tables.firstNotNullOfOrNull { t -> t.mapping[Field.ODOMETER]?.let { distanceUnitOf(t.table.header[it]) } },
            volumeUnit = tables.firstNotNullOfOrNull { t -> t.mapping[Field.VOLUME]?.let { volumeUnitOf(t.table.header[it]) } },
            vehicles = if (dialect == DialectId.FUEL_LOG) vehiclesIn(tables) else emptyList(),
            categoryNames = if (dialect == DialectId.FUELIO) categoriesIn(document) else emptyMap(),
        )
    }

    /**
     * The same file with a generic table's columns chosen by the user. The dates are read
     * again, since the date column may be a different one now.
     */
    fun remap(plan: ImportPlan, mapping: Map<Field, Int>): ImportPlan {
        if (plan.dialect != DialectId.GENERIC || plan.tables.isEmpty()) return plan
        val tables = listOf(plan.tables.first().copy(mapping = mapping))
        return plan.copy(
            tables = tables,
            dates = resolveDates(tables),
            distanceUnit = mapping[Field.ODOMETER]?.let { distanceUnitOf(plan.header[it]) },
            volumeUnit = mapping[Field.VOLUME]?.let { volumeUnitOf(plan.header[it]) },
        )
    }

    /** Text to sections to tables. An Excel `sep=` line, when there is one, decides the delimiter. */
    fun read(text: String): Pair<CsvDocument, Char> {
        val lines = text.lines()
        var start = 0
        val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
        val declared = firstNonBlank.takeIf { it >= 0 }?.let { DelimiterSniffer.declared(lines[it]) }
        if (declared != null) start = firstNonBlank + 1

        val chunks = mutableListOf<Triple<String?, Int, List<String>>>()
        var name: String? = null
        var first = start
        var body = mutableListOf<String>()
        for (i in start until lines.size) {
            val line = lines[i]
            if (line.startsWith(SECTION_MARKER)) {
                if (body.isNotEmpty() || name != null) chunks += Triple(name, first, body)
                name = line.removePrefix(SECTION_MARKER).trim()
                first = i + 1
                body = mutableListOf()
            } else {
                body += line
            }
        }
        chunks += Triple(name, first, body)

        var fileDelimiter = declared ?: ','
        val sections = chunks.mapNotNull { (sectionName, from, sectionLines) ->
            val delimiter = declared ?: DelimiterSniffer.sniff(sectionLines).delimiter
            val rows = CsvTokenizer.tokenize(sectionLines.joinToString("\n"), delimiter, firstLine = from + 1)
            if (rows.isEmpty()) return@mapNotNull null
            if (sectionName == null || sectionName.equals("Log", ignoreCase = true)) fileDelimiter = delimiter
            CsvSection(sectionName, CsvTable(rows.first().cells, rows.drop(1)))
        }
        return CsvDocument(sections) to fileDelimiter
    }

    private fun resolveDates(tables: List<MappedTable>): DateFormatResolver.Result =
        DateFormatResolver.resolve(
            tables.flatMap { t ->
                t.table.rows.map { row ->
                    DateSample(
                        row.cell(t.mapping[Field.DATE]),
                        DecimalParser.parseOrNull(row.cell(t.mapping[Field.ODOMETER]), NumberKind.ODOMETER),
                    )
                }
            },
        )

    private fun vehiclesIn(tables: List<MappedTable>): List<String> =
        tables.flatMap { t -> t.table.rows.map { it.cell(t.mapping[Field.VEHICLE]) } }.filter { it.isNotEmpty() }.distinct()

    /** Fuelio numbers its cost types and names them in a section of their own. */
    private fun categoriesIn(document: CsvDocument): Map<String, String> {
        val table = document.section("CostCategories") ?: return emptyMap()
        val mapping = Headers.map(
            table.header,
            listOf(ColumnSpec(Field.CATEGORY, listOf("CostTypeID", "id")), ColumnSpec(Field.TITLE, listOf("Name"))),
        )
        val id = mapping[Field.CATEGORY] ?: return emptyMap()
        val label = mapping[Field.TITLE] ?: return emptyMap()
        return table.rows.associate { it.cell(id) to it.cell(label) }
    }

    /** "Odometer (km)", "Odo (mi)", "Kilometerstand": the unit the header states, if it states one. */
    fun distanceUnitOf(header: String): DistanceUnit? {
        val parsed = Headers.parse(header)
        val unit = parsed.unit
        return when {
            unit != null && unit in KM -> DistanceUnit.KILOMETRE
            unit != null && unit in MILES -> DistanceUnit.MILE
            parsed.name in KM || parsed.name.startsWith("kilomet") || parsed.name.startsWith("km") -> DistanceUnit.KILOMETRE
            parsed.name in MILES -> DistanceUnit.MILE
            else -> null
        }
    }

    /** "Volume (gal)", "Fuel (litres)", "Liter": the unit the header states, if it states one. */
    fun volumeUnitOf(header: String): EnergyUnit? {
        val parsed = Headers.parse(header)
        return unitWord(parsed.unit) ?: unitWord(parsed.name)
    }

    private fun unitWord(text: String?): EnergyUnit? = when (text) {
        null -> null
        in LITRES -> EnergyUnit.LITRE
        in US_GALLONS -> EnergyUnit.US_GALLON
        in IMP_GALLONS -> EnergyUnit.IMP_GALLON
        else -> null
    }

    private val KM = setOf("km", "kms", "kilometers", "kilometres", "kilometer", "kilometre", "kilometros", "quilometros", "chilometri")
    private val MILES = setOf("mi", "mile", "miles", "meilen", "millas")
    private val LITRES = setOf("l", "ltr", "ltrs", "litre", "litres", "liter", "liters", "litros", "litri", "lts")
    private val US_GALLONS = setOf("gal", "gals", "gallon", "gallons", "us gal", "gal us", "us gallons", "galones")
    private val IMP_GALLONS = setOf("imp gal", "uk gal", "gal uk", "imperial gallons", "imp. gal")
}
