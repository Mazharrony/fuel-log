package com.fuelexpenselog.csv.imprt

import java.text.Normalizer
import java.util.Locale

/** What a column means, whatever it is called. */
enum class Field {
    DATE, TIME, ODOMETER, VOLUME, TOTAL, UNIT_PRICE, FULL, PARTIAL, MISSED, STATION, NOTE,
    CATEGORY, TITLE, VENDOR,

    // The app's own export, which carries exact canonical values beside the display ones.
    RECORD_TYPE, VEHICLE, VOLUME_UNIT, CURRENCY, TYPED_FIELD, TAG, ODOMETER_M, VOLUME_MICRO, TOTAL_MICROS,
}

enum class RecordKind { FILL_UPS, EXPENSES, MIXED }

/** One column: what it means, and every name it is known to go by. */
data class ColumnSpec(val field: Field, val aliases: List<String>, val required: Boolean = false)

/** A kind of table a dialect writes. A sectioned file names the section it lives in. */
data class TableSpec(val kind: RecordKind, val columns: List<ColumnSpec>, val section: String? = null)

enum class DialectId { FUEL_LOG, FUELIO, ACAR, DRIVVO, GENERIC }

/**
 * An app's export format, as data rather than code: which tables, and which columns under
 * which names.
 *
 * None of these could be checked against real files - there were none to hand - so every
 * alias here is a best effort, and the detector leans towards [DialectId.GENERIC], where the
 * user confirms the columns, rather than trusting a weak match. A wrong header name is a
 * one-line fix in this file; a fifth importer is one more table.
 */
data class Dialect(
    val id: DialectId,
    val tables: List<TableSpec>,
    /** Headers only this app writes. A file with none of them is never taken to be this app's. */
    val signature: List<String>,
    /** Sections open with this marker, like Fuelio's `## Log`. The file is split on it first. */
    val sectionMarker: String? = null,
)

/** Header text, made comparable: case, accents, spacing and a trailing unit set aside. */
object Headers {

    /** "Odometer (km)" is the name `odometer` with the unit `km`. */
    data class Parsed(val name: String, val unit: String?)

    private val UNIT = Regex("""\s*[(\[]([^)\]]*)[)\]]\s*$""")
    private val NOT_UNITS = setOf("optional", "opcional", "optionnel", "optional field")

    fun parse(raw: String): Parsed {
        var text = normalize(raw)
        var unit: String? = null
        // "Price (optional)" carries no unit; "Odo (km)" does. Peel both kinds off the end.
        while (true) {
            val match = UNIT.find(text) ?: break
            val inside = match.groupValues[1].trim()
            if (inside !in NOT_UNITS && unit == null) unit = inside
            text = text.substring(0, match.range.first).trim()
        }
        return Parsed(text.trimEnd('?', ':').trim(), unit)
    }

    /** Lower case, no accents, single spaces: "Odômetro " and "odometro" compare equal. */
    fun normalize(raw: String): String {
        val stripped = Normalizer.normalize(raw.trim().removePrefix("﻿"), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return stripped.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Each field's column in [header], by alias, first spec first. A column is used once, so
     * "Price" cannot be both the total and the unit price.
     */
    fun map(header: List<String>, columns: List<ColumnSpec>): Map<Field, Int> {
        val names = header.map { parse(it).name }
        val taken = mutableSetOf<Int>()
        val mapping = linkedMapOf<Field, Int>()
        for (spec in columns) {
            if (spec.field in mapping) continue
            val aliases = spec.aliases.map { parse(it).name }.toSet()
            val index = names.indices.firstOrNull { it !in taken && names[it] in aliases } ?: continue
            mapping[spec.field] = index
            taken += index
        }
        return mapping
    }
}

/** The tables. Aliases are compared through [Headers.parse], so case and accents do not matter. */
object Dialects {

    private val FUEL_LOG = Dialect(
        id = DialectId.FUEL_LOG,
        tables = listOf(
            TableSpec(
                RecordKind.MIXED,
                listOf(
                    ColumnSpec(Field.RECORD_TYPE, listOf("record_type"), required = true),
                    ColumnSpec(Field.VEHICLE, listOf("vehicle")),
                    ColumnSpec(Field.DATE, listOf("date"), required = true),
                    ColumnSpec(Field.ODOMETER, listOf("odometer")),
                    ColumnSpec(Field.VOLUME, listOf("volume")),
                    ColumnSpec(Field.VOLUME_UNIT, listOf("volume_unit")),
                    ColumnSpec(Field.FULL, listOf("full_tank")),
                    ColumnSpec(Field.MISSED, listOf("missed_previous")),
                    ColumnSpec(Field.TOTAL, listOf("total")),
                    ColumnSpec(Field.UNIT_PRICE, listOf("unit_price")),
                    ColumnSpec(Field.CURRENCY, listOf("currency")),
                    ColumnSpec(Field.TYPED_FIELD, listOf("typed_field")),
                    ColumnSpec(Field.CATEGORY, listOf("category")),
                    ColumnSpec(Field.TAG, listOf("tag")),
                    ColumnSpec(Field.STATION, listOf("station")),
                    ColumnSpec(Field.VENDOR, listOf("vendor")),
                    ColumnSpec(Field.NOTE, listOf("note")),
                    ColumnSpec(Field.ODOMETER_M, listOf("odometer_m")),
                    ColumnSpec(Field.VOLUME_MICRO, listOf("volume_micro")),
                    ColumnSpec(Field.TOTAL_MICROS, listOf("total_micros")),
                ),
            ),
        ),
        signature = listOf("record_type", "volume_micro", "total_micros"),
    )

    private val FUELIO = Dialect(
        id = DialectId.FUELIO,
        sectionMarker = "## ",
        tables = listOf(
            TableSpec(
                RecordKind.FILL_UPS,
                section = "Log",
                columns = listOf(
                    ColumnSpec(Field.DATE, listOf("Data", "Date"), required = true),
                    ColumnSpec(Field.ODOMETER, listOf("Odo")),
                    ColumnSpec(Field.VOLUME, listOf("Fuel"), required = true),
                    ColumnSpec(Field.FULL, listOf("Full")),
                    ColumnSpec(Field.TOTAL, listOf("Price")),
                    ColumnSpec(Field.UNIT_PRICE, listOf("VolumePrice")),
                    ColumnSpec(Field.MISSED, listOf("Missed")),
                    ColumnSpec(Field.STATION, listOf("City")),
                    ColumnSpec(Field.NOTE, listOf("Notes")),
                ),
            ),
            TableSpec(
                RecordKind.EXPENSES,
                section = "Costs",
                columns = listOf(
                    ColumnSpec(Field.DATE, listOf("Date", "Data"), required = true),
                    ColumnSpec(Field.TITLE, listOf("CostTitle")),
                    ColumnSpec(Field.ODOMETER, listOf("Odo")),
                    ColumnSpec(Field.CATEGORY, listOf("CostTypeID")),
                    ColumnSpec(Field.NOTE, listOf("Notes")),
                    ColumnSpec(Field.TOTAL, listOf("Cost"), required = true),
                ),
            ),
        ),
        signature = listOf("CostTypeID", "VolumePrice", "TankNumber", "ExcludeDistance", "Missed"),
    )

    private val ACAR = Dialect(
        id = DialectId.ACAR,
        tables = listOf(
            TableSpec(
                RecordKind.FILL_UPS,
                listOf(
                    ColumnSpec(Field.DATE, listOf("Date"), required = true),
                    ColumnSpec(Field.TIME, listOf("Time")),
                    ColumnSpec(Field.ODOMETER, listOf("Odometer Reading", "Odometer"), required = true),
                    ColumnSpec(Field.VOLUME, listOf("Volume", "Fuel Volume"), required = true),
                    ColumnSpec(Field.UNIT_PRICE, listOf("Price per Unit", "Price")),
                    ColumnSpec(Field.TOTAL, listOf("Total Cost", "Total")),
                    // Inverted: "Yes" means the tank was NOT filled.
                    ColumnSpec(Field.PARTIAL, listOf("Partial Fill-up", "Partial Fill-Up?", "Partial")),
                    ColumnSpec(Field.MISSED, listOf("Previous Missed Fill-ups", "Missed Fill-up")),
                    ColumnSpec(Field.STATION, listOf("Fuel Station", "Station")),
                    ColumnSpec(Field.NOTE, listOf("Notes")),
                ),
            ),
        ),
        signature = listOf("Partial Fill-up", "Partial Fill-Up?", "Previous Missed Fill-ups", "Fuel Brand"),
    )

    private val DRIVVO = Dialect(
        id = DialectId.DRIVVO,
        tables = listOf(
            TableSpec(
                RecordKind.FILL_UPS,
                listOf(
                    ColumnSpec(Field.DATE, listOf("Data", "Fecha", "Date", "Datum"), required = true),
                    ColumnSpec(Field.ODOMETER, listOf("Odômetro", "Odómetro", "Odometer", "Kilometerstand"), required = true),
                    ColumnSpec(Field.VOLUME, listOf("Litros", "Litres", "Liters", "Volume", "Liter"), required = true),
                    ColumnSpec(Field.UNIT_PRICE, listOf("Preço", "Precio", "Price", "Preis")),
                    ColumnSpec(Field.TOTAL, listOf("Valor total", "Costo total", "Total cost", "Gesamtpreis", "Total")),
                    ColumnSpec(Field.FULL, listOf("Tanque cheio", "Tanque lleno", "Full tank", "Tank voll")),
                    ColumnSpec(Field.STATION, listOf("Posto", "Gasolinera", "Gas station", "Tankstelle")),
                    ColumnSpec(Field.NOTE, listOf("Observação", "Observaciones", "Notes", "Notiz")),
                ),
            ),
        ),
        signature = listOf("Tanque cheio", "Tanque lleno", "Posto", "Odômetro", "Odómetro"),
    )

    /**
     * Any other file with a header row: fill-ups only, columns guessed from every alias this
     * app knows, in five languages, and confirmed by the user before anything is imported.
     */
    val GENERIC_FILL_UPS: TableSpec = TableSpec(
        RecordKind.FILL_UPS,
        listOf(
            ColumnSpec(Field.DATE, listOf("date", "datum", "data", "fecha", "day", "date/time", "datetime", "fecha y hora"), required = true),
            ColumnSpec(Field.TIME, listOf("time", "uhrzeit", "zeit", "hora", "heure", "ora")),
            ColumnSpec(
                Field.ODOMETER,
                listOf(
                    "odometer", "odometer reading", "odo", "mileage", "odometer (km)", "km", "kilometers",
                    "kilometres", "miles", "kilometerstand", "km-stand", "tachostand", "odometro",
                    "quilometragem", "kilometraje", "compteur", "kilometrage", "contachilometri",
                ),
                required = true,
            ),
            ColumnSpec(
                Field.VOLUME,
                listOf(
                    "volume", "liters", "litres", "liter", "litre", "l", "gallons", "gal", "fuel",
                    "fuel amount", "quantity", "amount of fuel", "menge", "getankt", "litros",
                    "galones", "quantite", "litri", "fuel (litres)",
                ),
                required = true,
            ),
            ColumnSpec(
                Field.TOTAL,
                listOf(
                    "total", "total cost", "total price", "cost", "amount", "sum", "paid", "betrag",
                    "gesamt", "gesamtpreis", "kosten", "valor total", "valor", "costo total",
                    "precio total", "importe", "montant", "prix total", "totale", "importo",
                ),
            ),
            ColumnSpec(
                Field.UNIT_PRICE,
                listOf(
                    "price", "unit price", "price per unit", "price per liter", "price per litre",
                    "price per gallon", "price/l", "preis", "preis/l", "literpreis", "preco",
                    "precio", "prix", "prix/l", "prezzo",
                ),
            ),
            ColumnSpec(
                Field.FULL,
                listOf("full", "full tank", "full fill", "filled", "voll", "volltank", "vollgetankt", "tanque cheio", "tanque lleno", "plein", "pieno"),
            ),
            ColumnSpec(Field.PARTIAL, listOf("partial", "partial fill", "partial fill-up", "teilbetankung", "parcial")),
            ColumnSpec(Field.MISSED, listOf("missed", "missed fill-up", "missed previous")),
            ColumnSpec(Field.STATION, listOf("station", "fuel station", "gas station", "petrol station", "tankstelle", "posto", "gasolinera", "station-service", "distributore")),
            ColumnSpec(Field.NOTE, listOf("note", "notes", "comment", "comments", "notiz", "bemerkung", "observacao", "observaciones", "remarque", "nota")),
        ),
    )

    val GENERIC = Dialect(DialectId.GENERIC, listOf(GENERIC_FILL_UPS), signature = emptyList())

    /** Every dialect the detector scores. Generic is the fallback, never a candidate. */
    val SCORED: List<Dialect> = listOf(FUEL_LOG, FUELIO, ACAR, DRIVVO)

    fun of(id: DialectId): Dialect = (SCORED + GENERIC).first { it.id == id }
}
